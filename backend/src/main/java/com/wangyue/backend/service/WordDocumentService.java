package com.wangyue.backend.service;

import com.wangyue.backend.dto.RecognizedQuestion;
import com.wangyue.backend.dto.RecognizedQuestionOption;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Service;

@Service
public class WordDocumentService {

    private static final Pattern QUESTION_START = Pattern.compile("^\\s*(\\d{1,4})[.．、]\\s*(\\S.*)$");
    private static final Pattern INLINE_OPTION_START = Pattern.compile("(?<!\\S)([A-Ha-h])[.．、:：)）]\\s*");
    private static final Pattern ANSWER_START = Pattern.compile("^\\s*(?:【\\s*)?(?:(?:参考|正确)\\s*)?答案\\s*(?:】)?\\s*[:：]?\\s*(.*)$");
    private static final Pattern EXPLANATION_START = Pattern.compile("^\\s*(?:【\\s*)?(?:答案)?(?:解析|分析|说明)\\s*(?:】)?\\s*[:：]?\\s*(.*)$");

    public String extractText(Path documentPath) {
        Path normalizedDocumentPath = documentPath.toAbsolutePath().normalize();
        if (!Files.isRegularFile(normalizedDocumentPath)) {
            throw new IllegalArgumentException("待解析的 Word 文件不存在");
        }

        try (
            InputStream inputStream = Files.newInputStream(normalizedDocumentPath);
            XWPFDocument document = new XWPFDocument(inputStream)
        ) {
            String text = document.getParagraphs().stream()
                .map(paragraph -> paragraph.getText().trim())
                .filter(paragraphText -> !paragraphText.isBlank())
                .collect(Collectors.joining(System.lineSeparator()));

            if (text.isBlank()) {
                throw new IllegalStateException("Word 文件中没有读取到可用文字");
            }
            return text;
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new IllegalStateException("Word 文件解析失败，请确认文件未损坏且格式为 .docx", exception);
        }
    }

    /**
     * Splits a Word document on question-number boundaries before it is sent
     * to the local LLM. This keeps each request small enough for a local
     * model, while preserving the original paragraphs of every question.
     */
    public QuestionTextChunks splitQuestionText(String recognitionText, int questionsPerChunk) {
        return splitQuestionText(recognitionText, questionsPerChunk, Integer.MAX_VALUE);
    }

    /**
     * Besides question count, limit the text size of one request. A very long
     * question may otherwise fill the local model's 4096-token context even
     * when the batch contains only a few questions.
     */
    public QuestionTextChunks splitQuestionText(
        String recognitionText, int questionsPerChunk, int maxCharactersPerChunk
    ) {
        if (recognitionText == null || recognitionText.isBlank()) {
            throw new IllegalArgumentException("没有可切分的 Word 文字");
        }
        if (questionsPerChunk < 1) {
            throw new IllegalArgumentException("每批题目数量必须大于 0");
        }
        if (maxCharactersPerChunk < 1) {
            throw new IllegalArgumentException("每批文字长度必须大于 0");
        }

        List<String> chunks = new ArrayList<>();
        StringBuilder currentChunk = new StringBuilder();
        int currentChunkQuestionCount = 0;
        int estimatedQuestionCount = 0;

        for (String line : recognitionText.split("\\R")) {
            boolean isQuestionStart = QUESTION_START.matcher(line).matches();
            if (isQuestionStart && currentChunkQuestionCount > 0) {
                int nextLineLength = line.length() + (currentChunk.length() > 0 ? System.lineSeparator().length() : 0);
                boolean reachedQuestionLimit = currentChunkQuestionCount >= questionsPerChunk;
                boolean wouldExceedTextLimit = currentChunk.length() + nextLineLength > maxCharactersPerChunk;
                if (reachedQuestionLimit || wouldExceedTextLimit) {
                    addChunk(chunks, currentChunk);
                    currentChunkQuestionCount = 0;
                }
            }

            if (currentChunk.length() > 0) {
                currentChunk.append(System.lineSeparator());
            }
            currentChunk.append(line);

            if (isQuestionStart) {
                estimatedQuestionCount++;
                currentChunkQuestionCount++;
            }
        }
        addChunk(chunks, currentChunk);

        if (chunks.isEmpty()) {
            throw new IllegalStateException("Word 文件中没有可生成题目的文字");
        }
        return new QuestionTextChunks(List.copyOf(chunks), estimatedQuestionCount);
    }

    /**
     * Fast path for Word files whose text already has question numbers,
     * options and answer lines. Unlike the image pipeline, Word text does not
     * need to be sent to the local LLM one small batch at a time.
     *
     * <p>Every question is returned, including incomplete ones. The draft
     * service later marks incomplete data as NEEDS_REVIEW, so one malformed
     * question cannot hide or stop the questions after it.</p>
     */
    public ParsedQuestions parseStructuredQuestions(String recognitionText) {
        if (recognitionText == null || recognitionText.isBlank()) {
            throw new IllegalArgumentException("没有可解析的 Word 文字");
        }

        List<QuestionBlock> blocks = new ArrayList<>();
        StringBuilder currentQuestion = null;
        String currentTypeHint = null;
        String currentQuestionTypeHint = null;

        for (String rawLine : recognitionText.split("\\R")) {
            String line = rawLine.trim();
            if (line.isBlank()) {
                continue;
            }
            if (isQuestionTypeHint(line)) {
                // Type headings usually sit between two questions. Keep the
                // heading for the next numbered question instead of adding it
                // to the previous question's stem.
                currentTypeHint = line;
                continue;
            }
            java.util.regex.Matcher questionMatcher = QUESTION_START.matcher(line);
            if (questionMatcher.matches()) {
                if (currentQuestion != null) {
                    blocks.add(new QuestionBlock(currentQuestion.toString(), currentQuestionTypeHint));
                }
                currentQuestion = new StringBuilder(questionMatcher.group(2));
                currentQuestionTypeHint = currentTypeHint;
                continue;
            }

            if (currentQuestion == null) {
                continue;
            }
            currentQuestion.append(System.lineSeparator()).append(line);
        }
        if (currentQuestion != null) {
            blocks.add(new QuestionBlock(currentQuestion.toString(), currentQuestionTypeHint));
        }
        if (blocks.isEmpty()) {
            throw new IllegalStateException("Word 中没有找到以题号开头的题目，请检查题号格式");
        }

        List<RecognizedQuestion> questions = blocks.stream()
            .map(this::parseQuestionBlock)
            .toList();
        return new ParsedQuestions(questions, blocks.size());
    }

    private RecognizedQuestion parseQuestionBlock(QuestionBlock block) {
        List<RecognizedQuestionOption> options = new ArrayList<>();
        List<String> answerTokens = new ArrayList<>();
        List<String> stemLines = new ArrayList<>();
        List<String> explanationLines = new ArrayList<>();
        boolean readingExplanation = false;

        for (String line : block.text().split("\\R")) {
            java.util.regex.Matcher answerMatcher = ANSWER_START.matcher(line);
            java.util.regex.Matcher explanationMatcher = EXPLANATION_START.matcher(line);
            List<RecognizedQuestionOption> lineOptions = parseOptionsFromLine(line);
            if (!lineOptions.isEmpty()) {
                options.addAll(lineOptions);
                readingExplanation = false;
            } else if (answerMatcher.matches()) {
                answerTokens.addAll(extractAnswerTokens(answerMatcher.group(1)));
                readingExplanation = false;
            } else if (explanationMatcher.matches()) {
                String explanation = explanationMatcher.group(1).trim();
                if (!explanation.isBlank()) {
                    explanationLines.add(explanation);
                }
                readingExplanation = true;
            } else if (readingExplanation) {
                explanationLines.add(line);
            } else {
                stemLines.add(line);
            }
        }

        RecognizedQuestion question = new RecognizedQuestion();
        question.setQuestionType(resolveQuestionType(block.typeHint(), answerTokens));
        question.setStem(String.join(System.lineSeparator(), stemLines).trim());
        question.setOptions(options);
        question.setCorrectAnswer(answerTokens);
        question.setExplanation(explanationLines.isEmpty() ? null : String.join(System.lineSeparator(), explanationLines));
        question.setKnowledgePoints(List.of());
        return question;
    }

    /** Supports both one option per line and Word's common compact form:
     * {@code A.建议 B.申诉 C.要求 D.抗议}. */
    private List<RecognizedQuestionOption> parseOptionsFromLine(String line) {
        java.util.regex.Matcher matcher = INLINE_OPTION_START.matcher(line);
        List<OptionMarker> markers = new ArrayList<>();
        while (matcher.find()) {
            markers.add(new OptionMarker(matcher.group(1), matcher.start(), matcher.end()));
        }
        if (markers.isEmpty()) {
            return List.of();
        }

        List<RecognizedQuestionOption> options = new ArrayList<>();
        for (int index = 0; index < markers.size(); index++) {
            OptionMarker marker = markers.get(index);
            int contentEnd = index + 1 < markers.size() ? markers.get(index + 1).start() : line.length();
            String content = line.substring(marker.contentStart(), contentEnd).trim();
            if (content.isBlank()) {
                continue;
            }
            RecognizedQuestionOption option = new RecognizedQuestionOption();
            option.setOptionKey(marker.key().toUpperCase(java.util.Locale.ROOT));
            option.setContent(content);
            options.add(option);
        }
        return options;
    }

    private boolean isQuestionTypeHint(String line) {
        return line.contains("单选") || line.contains("单项") || line.contains("多选") || line.contains("多项")
            || line.contains("判断") || line.contains("是非");
    }

    private String resolveQuestionType(String typeHint, List<String> answerTokens) {
        if (typeHint != null && (typeHint.contains("判断") || typeHint.contains("是非"))) {
            return "TRUE_FALSE";
        }
        if (typeHint != null && (typeHint.contains("多选") || typeHint.contains("多项"))) {
            return "MULTIPLE_CHOICE";
        }
        return answerTokens.size() > 1 ? "MULTIPLE_CHOICE" : "SINGLE_CHOICE";
    }

    private List<String> extractAnswerTokens(String answerText) {
        if (answerText == null || answerText.isBlank()) {
            return List.of();
        }
        java.util.regex.Matcher letterMatcher = Pattern.compile("[A-Ha-h]").matcher(answerText);
        List<String> letters = new ArrayList<>();
        while (letterMatcher.find()) {
            String letter = letterMatcher.group().toUpperCase(java.util.Locale.ROOT);
            if (!letters.contains(letter)) {
                letters.add(letter);
            }
        }
        if (!letters.isEmpty()) {
            return letters;
        }
        return List.of(answerText.trim());
    }

    private void addChunk(List<String> chunks, StringBuilder chunk) {
        String text = chunk.toString().trim();
        if (!text.isBlank()) {
            chunks.add(text);
        }
        chunk.setLength(0);
    }

    public record QuestionTextChunks(List<String> chunks, int estimatedQuestionCount) {}

    public record ParsedQuestions(List<RecognizedQuestion> questions, int estimatedQuestionCount) {}

    private record QuestionBlock(String text, String typeHint) {}

    private record OptionMarker(String key, int start, int contentStart) {}
}
