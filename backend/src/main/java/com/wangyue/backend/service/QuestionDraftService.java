package com.wangyue.backend.service;

import com.wangyue.backend.dto.RecognizedQuestion;
import com.wangyue.backend.dto.RecognizedQuestionOption;
import com.wangyue.backend.entity.ImportFile;
import com.wangyue.backend.entity.ImportBatch;
import com.wangyue.backend.entity.QuestionDraft;
import com.wangyue.backend.entity.QuestionDraftOption;
import com.wangyue.backend.mapper.ImportFileMapper;
import com.wangyue.backend.mapper.ImportBatchMapper;
import com.wangyue.backend.mapper.QuestionDraftMapper;
import com.wangyue.backend.mapper.QuestionDraftOptionMapper;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class QuestionDraftService {

    private static final Set<String> SUPPORTED_TYPES = Set.of(
        "SINGLE_CHOICE", "MULTIPLE_CHOICE", "TRUE_FALSE"
    );

    private final ImportFileMapper importFileMapper;
    private final ImportBatchMapper importBatchMapper;
    private final QuestionDraftMapper questionDraftMapper;
    private final QuestionDraftOptionMapper questionDraftOptionMapper;
    private final ObjectMapper objectMapper;

    public QuestionDraftService(
        ImportFileMapper importFileMapper,
        ImportBatchMapper importBatchMapper,
        QuestionDraftMapper questionDraftMapper,
        QuestionDraftOptionMapper questionDraftOptionMapper,
        ObjectMapper objectMapper
    ) {
        this.importFileMapper = importFileMapper;
        this.importBatchMapper = importBatchMapper;
        this.questionDraftMapper = questionDraftMapper;
        this.questionDraftOptionMapper = questionDraftOptionMapper;
        this.objectMapper = objectMapper;
    }

    /**
     * 由 OCR / LLM 编排层调用：保存原始文字和经过校验的临时草稿。
     * 这里绝不会创建正式 Question。
     */
    @Transactional
    public void saveRecognitionResult(
        Long importFileId,
        String recognitionText,
        List<RecognizedQuestion> recognizedQuestions
    ) {
        ImportFile importFile = importFileMapper.selectById(importFileId);
        if (importFile == null) {
            throw new IllegalArgumentException("导入文件不存在");
        }
        if (recognizedQuestions == null || recognizedQuestions.isEmpty()) {
            throw new IllegalArgumentException("未识别出可确认的题目");
        }

        for (RecognizedQuestion question : recognizedQuestions) {
            validateRecognizedQuestion(question);
        }

        importFile.setRecognitionText(recognitionText);
        importFile.setStatus("WAITING_CONFIRMATION");
        importFile.setErrorMessage(null);
        importFileMapper.updateById(importFile);

        for (int questionIndex = 0; questionIndex < recognizedQuestions.size(); questionIndex++) {
            saveDraft(importFile, recognizedQuestions.get(questionIndex), questionIndex + 1);
        }
    }

    private void validateRecognizedQuestion(RecognizedQuestion question) {
        if (question == null || question.getStem() == null || question.getStem().isBlank()) {
            throw new IllegalArgumentException("识别结果缺少题干");
        }
        if (!SUPPORTED_TYPES.contains(question.getQuestionType())) {
            throw new IllegalArgumentException("识别结果的题型不支持");
        }

        List<RecognizedQuestionOption> options = question.getOptions() == null ? List.of() : question.getOptions();
        Set<String> optionKeys = new HashSet<>();
        for (RecognizedQuestionOption option : options) {
            if (option == null || option.getOptionKey() == null || option.getOptionKey().isBlank()
                || !optionKeys.add(option.getOptionKey())) {
                throw new IllegalArgumentException("识别结果的选项标识无效或重复");
            }
        }

        List<String> answers = question.getCorrectAnswer() == null ? List.of() : question.getCorrectAnswer();
        if (!optionKeys.containsAll(answers)) {
            throw new IllegalArgumentException("识别结果的答案不属于当前选项");
        }
        if (!"MULTIPLE_CHOICE".equals(question.getQuestionType()) && answers.size() > 1) {
            throw new IllegalArgumentException("单选题和判断题最多只能有一个答案");
        }
    }

    private void saveDraft(ImportFile importFile, RecognizedQuestion recognizedQuestion, int sortOrder) {
        QuestionDraft draft = new QuestionDraft();
        draft.setLibraryId(findLibraryId(importFile));
        draft.setImportFileId(importFile.getId());
        draft.setSortOrder(sortOrder);
        draft.setStatus("WAITING_CONFIRMATION");
        draft.setQuestionType(recognizedQuestion.getQuestionType());
        draft.setStem(recognizedQuestion.getStem().trim());
        draft.setCorrectAnswer(toJsonOrNull(recognizedQuestion.getCorrectAnswer()));
        draft.setExplanation(blankToNull(recognizedQuestion.getExplanation()));
        draft.setKnowledgePoints(toJsonOrNull(recognizedQuestion.getKnowledgePoints()));
        questionDraftMapper.insert(draft);

        List<RecognizedQuestionOption> options = recognizedQuestion.getOptions() == null
            ? List.of() : recognizedQuestion.getOptions();
        for (int optionIndex = 0; optionIndex < options.size(); optionIndex++) {
            RecognizedQuestionOption recognizedOption = options.get(optionIndex);
            QuestionDraftOption option = new QuestionDraftOption();
            option.setQuestionDraftId(draft.getId());
            option.setOptionKey(recognizedOption.getOptionKey().trim());
            option.setContent(blankToNull(recognizedOption.getContent()));
            option.setSortOrder(optionIndex + 1);
            questionDraftOptionMapper.insert(option);
        }
    }

    private Long findLibraryId(ImportFile importFile) {
        ImportBatch batch = importBatchMapper.selectById(importFile.getImportBatchId());
        if (batch == null) {
            throw new IllegalStateException("导入批次不存在");
        }
        return batch.getLibraryId();
    }

    private String toJsonOrNull(List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(values);
        } catch (JacksonException exception) {
            throw new IllegalStateException("识别结果无法保存", exception);
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
