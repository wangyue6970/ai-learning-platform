package com.wangyue.backend.service;

import com.wangyue.backend.dto.RecognizedQuestion;
import com.wangyue.backend.dto.RecognizedQuestionOption;
import com.wangyue.backend.dto.CreateQuestionOptionRequest;
import com.wangyue.backend.dto.UpdateQuestionDraftRequest;
import com.wangyue.backend.exception.OperationConflictException;
import com.wangyue.backend.entity.ImportFile;
import com.wangyue.backend.entity.ImportBatch;
import com.wangyue.backend.entity.QuestionDraft;
import com.wangyue.backend.entity.QuestionDraftOption;
import com.wangyue.backend.entity.Question;
import com.wangyue.backend.mapper.ImportFileMapper;
import com.wangyue.backend.mapper.ImportBatchMapper;
import com.wangyue.backend.mapper.QuestionDraftMapper;
import com.wangyue.backend.mapper.QuestionDraftOptionMapper;
import com.wangyue.backend.dto.CreateQuestionRequest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
    private final QuestionService questionService;
    private final ObjectMapper objectMapper;

    public QuestionDraftService(
        ImportFileMapper importFileMapper,
        ImportBatchMapper importBatchMapper,
        QuestionDraftMapper questionDraftMapper,
        QuestionDraftOptionMapper questionDraftOptionMapper,
        QuestionService questionService,
        ObjectMapper objectMapper
    ) {
        this.importFileMapper = importFileMapper;
        this.importBatchMapper = importBatchMapper;
        this.questionDraftMapper = questionDraftMapper;
        this.questionDraftOptionMapper = questionDraftOptionMapper;
        this.questionService = questionService;
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

        importFile.setRecognitionText(recognitionText);
        importFile.setStatus("WAITING_CONFIRMATION");
        importFile.setErrorMessage(null);
        importFileMapper.updateById(importFile);

        appendRecognitionResult(importFileId, recognizedQuestions, 1);
    }

    /**
     * Saves one finished LLM chunk immediately. Unlike saveRecognitionResult,
     * this never changes the file state, because a larger Word document may
     * still have later chunks processing.
     */
    @Transactional
    public int appendRecognitionResult(
        Long importFileId,
        List<RecognizedQuestion> recognizedQuestions,
        int firstSortOrder
    ) {
        if (recognizedQuestions == null || recognizedQuestions.isEmpty()) {
            return 0;
        }
        if (firstSortOrder < 1) {
            throw new IllegalArgumentException("草稿排序号必须大于 0");
        }

        ImportFile importFile = importFileMapper.selectById(importFileId);
        if (importFile == null) {
            throw new IllegalArgumentException("导入文件不存在");
        }
        for (int questionIndex = 0; questionIndex < recognizedQuestions.size(); questionIndex++) {
            // One malformed AI result is saved as a visible repairable draft;
            // it must not block the remaining questions in a large Word file.
            saveDraft(importFile, prepareRecognizedQuestion(recognizedQuestions.get(questionIndex)),
                firstSortOrder + questionIndex);
        }
        return recognizedQuestions.size();
    }

    /**
     * 保存用户对草稿的修正。这里不创建正式 Question；正式入库由之后的确认动作单独完成。
     */
    @Transactional
    public void updateDraft(
        Long libraryId,
        Long importFileId,
        Long draftId,
        UpdateQuestionDraftRequest request
    ) {
        QuestionDraft draft = questionDraftMapper.selectById(draftId);
        if (draft == null || !libraryId.equals(draft.getLibraryId())
            || !importFileId.equals(draft.getImportFileId())) {
            throw new IllegalArgumentException("题目草稿不属于当前学习库或导入文件");
        }
        if (!"WAITING_CONFIRMATION".equals(draft.getStatus()) && !"NEEDS_REVIEW".equals(draft.getStatus())) {
            throw new OperationConflictException("草稿状态已变化，请刷新后重试");
        }

        validateDraftUpdateRequest(request);
        draft.setQuestionType(request.getQuestionType().trim());
        draft.setStem(request.getStem().trim());
        draft.setCorrectAnswer(toJsonOrNull(request.getCorrectAnswer()));
        draft.setExplanation(blankToNull(request.getExplanation()));
        draft.setKnowledgePoints(toJsonOrNull(request.getKnowledgePoints()));
        draft.setStatus("WAITING_CONFIRMATION");
        draft.setIssueReason(null);
        questionDraftMapper.updateById(draft);

        questionDraftOptionMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<QuestionDraftOption>()
            .eq(QuestionDraftOption::getQuestionDraftId, draftId));
        for (int index = 0; index < request.getOptions().size(); index++) {
            CreateQuestionOptionRequest requestOption = request.getOptions().get(index);
            QuestionDraftOption option = new QuestionDraftOption();
            option.setQuestionDraftId(draftId);
            option.setOptionKey(requestOption.getOptionKey().trim());
            option.setContent(requestOption.getContent().trim());
            option.setSortOrder(index + 1);
            questionDraftOptionMapper.insert(option);
        }
    }

    /**
     * 用户明确确认后，才将一份草稿复制为正式题目。
     * 创建题目、选项和修改草稿状态处在同一个事务中，任一步失败都会回滚。
     */
    @Transactional
    public void confirmDraft(Long libraryId, Long importFileId, Long draftId) {
        QuestionDraft draft = questionDraftMapper.selectById(draftId);
        if (draft == null || !libraryId.equals(draft.getLibraryId())
            || !importFileId.equals(draft.getImportFileId())) {
            throw new IllegalArgumentException("题目草稿不属于当前学习库或导入文件");
        }
        if (!"WAITING_CONFIRMATION".equals(draft.getStatus())) {
            throw new OperationConflictException("这道草稿已经处理过，请刷新后重试");
        }

        List<QuestionDraftOption> draftOptions = questionDraftOptionMapper.selectList(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<QuestionDraftOption>()
                .eq(QuestionDraftOption::getQuestionDraftId, draftId)
                .orderByAsc(QuestionDraftOption::getSortOrder)
        );
        List<String> answers = readStringList(draft.getCorrectAnswer());
        validateConfirmableDraft(draft, draftOptions, answers);

        CreateQuestionRequest request = new CreateQuestionRequest();
        request.setLibraryId(libraryId);
        request.setQuestionType(draft.getQuestionType());
        request.setStem(draft.getStem());
        request.setCorrectAnswer(answers);
        request.setExplanation(draft.getExplanation());
        request.setOptions(draftOptions.stream().map(option -> {
            CreateQuestionOptionRequest optionRequest = new CreateQuestionOptionRequest();
            optionRequest.setOptionKey(option.getOptionKey());
            optionRequest.setContent(option.getContent());
            optionRequest.setSortOrder(option.getSortOrder());
            return optionRequest;
        }).toList());

        Question formalQuestion = questionService.create(request);
        if (formalQuestion.getId() == null) {
            throw new IllegalStateException("正式题目保存失败");
        }
        draft.setStatus("CONFIRMED");
        questionDraftMapper.updateById(draft);
    }

    /**
     * Discarding is different from confirming: it deletes only temporary AI
     * data and never creates a formal question.
     */
    @Transactional
    public void discardDraft(Long libraryId, Long importFileId, Long draftId) {
        QuestionDraft draft = questionDraftMapper.selectById(draftId);
        if (draft == null || !libraryId.equals(draft.getLibraryId())
            || !importFileId.equals(draft.getImportFileId())) {
            throw new IllegalArgumentException("题目草稿不属于当前学习库或导入文件");
        }
        if (!"WAITING_CONFIRMATION".equals(draft.getStatus()) && !"NEEDS_REVIEW".equals(draft.getStatus())) {
            throw new OperationConflictException("这道草稿已经处理过，请刷新后重试");
        }

        questionDraftOptionMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<QuestionDraftOption>()
            .eq(QuestionDraftOption::getQuestionDraftId, draftId));
        questionDraftMapper.deleteById(draftId);
    }

    /**
     * AI output is untrusted. We keep everything that a user can reasonably
     * repair, attach an issue message, and let the remaining Word chunks run.
     */
    private PreparedDraft prepareRecognizedQuestion(RecognizedQuestion recognizedQuestion) {
        List<String> issues = new ArrayList<>();
        if (recognizedQuestion == null) {
            return new PreparedDraft(
                "SINGLE_CHOICE", "（AI 未能生成这道题的题干，请根据原文补充）", List.of(), List.of(), null,
                List.of(), "AI 未能生成完整题目，请补充题干、选项和正确答案。"
            );
        }

        String questionType = recognizedQuestion.getQuestionType();
        if (!SUPPORTED_TYPES.contains(questionType)) {
            questionType = "SINGLE_CHOICE";
            issues.add("题型识别不完整，请选择正确题型");
        }

        String stem = blankToNull(recognizedQuestion.getStem());
        if (stem == null) {
            stem = "（AI 未能生成这道题的题干，请根据原文补充）";
            issues.add("未识别出题干");
        }

        List<RecognizedQuestionOption> options = new ArrayList<>();
        Set<String> optionKeys = new HashSet<>();
        List<RecognizedQuestionOption> rawOptions = recognizedQuestion.getOptions() == null
            ? List.of() : recognizedQuestion.getOptions();
        for (RecognizedQuestionOption option : rawOptions) {
            if (option == null || blankToNull(option.getOptionKey()) == null) {
                issues.add("存在缺少选项标识的选项");
                continue;
            }
            String optionKey = option.getOptionKey().trim().toUpperCase(Locale.ROOT);
            if (!optionKeys.add(optionKey)) {
                issues.add("存在重复选项标识");
                continue;
            }
            RecognizedQuestionOption cleanedOption = new RecognizedQuestionOption();
            cleanedOption.setOptionKey(optionKey);
            cleanedOption.setContent(blankToNull(option.getContent()));
            options.add(cleanedOption);
        }
        if (options.isEmpty()) {
            issues.add("未识别出有效选项");
        }

        List<String> rawAnswers = recognizedQuestion.getCorrectAnswer() == null
            ? List.of() : recognizedQuestion.getCorrectAnswer();
        List<String> answers = normalizeAnswers(rawAnswers, options);
        if (answers.isEmpty()) {
            String rawAnswerText = rawAnswers.stream().filter(value -> value != null && !value.isBlank())
                .map(String::trim).reduce((left, right) -> left + "、" + right).orElse("未识别到答案");
            issues.add("AI 给出的答案“" + rawAnswerText + "”无法对应当前选项，请选择正确答案");
        }
        if (!"MULTIPLE_CHOICE".equals(questionType) && answers.size() > 1) {
            answers = List.of();
            issues.add("单选题或判断题识别出了多个答案，请选择一个正确答案");
        }

        return new PreparedDraft(
            questionType, stem, options, answers, blankToNull(recognizedQuestion.getExplanation()),
            recognizedQuestion.getKnowledgePoints() == null ? List.of() : recognizedQuestion.getKnowledgePoints(),
            issues.isEmpty() ? null : String.join("；", issues) + "。"
        );
    }

    /** Maps common LLM forms such as “B.” and “选 B” back to an option key. */
    private List<String> normalizeAnswers(List<String> rawAnswers, List<RecognizedQuestionOption> options) {
        Map<String, String> answerLookup = new HashMap<>();
        for (RecognizedQuestionOption option : options) {
            answerLookup.put(normalizeAnswerToken(option.getOptionKey()), option.getOptionKey());
            if (blankToNull(option.getContent()) != null) {
                answerLookup.put(normalizeAnswerToken(option.getContent()), option.getOptionKey());
            }
        }

        List<String> answers = new ArrayList<>();
        for (String rawAnswer : rawAnswers) {
            if (rawAnswer == null || rawAnswer.isBlank()) {
                continue;
            }
            String normalized = normalizeAnswerToken(rawAnswer);
            String matchedOptionKey = answerLookup.get(normalized);
            if (matchedOptionKey == null && normalized.length() > 1) {
                matchedOptionKey = answerLookup.get(normalized.substring(normalized.length() - 1));
            }
            if (matchedOptionKey != null && !answers.contains(matchedOptionKey)) {
                answers.add(matchedOptionKey);
            }
        }
        return answers;
    }

    private String normalizeAnswerToken(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT)
            .replaceAll("^(正确答案|答案|选项|选)\\s*[是为:：]*\\s*", "")
            .replaceAll("[.。、;；\\s]+$", "");
    }

    private void validateDraftUpdateRequest(UpdateQuestionDraftRequest request) {
        if (request == null || request.getQuestionType() == null || request.getQuestionType().isBlank()
            || request.getStem() == null || request.getStem().isBlank()
            || request.getOptions() == null || request.getOptions().isEmpty()) {
            throw new IllegalArgumentException("题型、题干和选项不能为空");
        }
        if (!SUPPORTED_TYPES.contains(request.getQuestionType().trim())) {
            throw new IllegalArgumentException("题目类型不支持");
        }

        Set<String> optionKeys = new HashSet<>();
        for (CreateQuestionOptionRequest option : request.getOptions()) {
            if (option == null || option.getOptionKey() == null || option.getOptionKey().isBlank()
                || option.getContent() == null || option.getContent().isBlank()
                || !optionKeys.add(option.getOptionKey().trim())) {
                throw new IllegalArgumentException("选项内容不能为空，且选项标识不能重复");
            }
        }

        List<String> answers = request.getCorrectAnswer() == null ? List.of() : request.getCorrectAnswer();
        if (!optionKeys.containsAll(answers)) {
            throw new IllegalArgumentException("识别答案必须属于当前选项");
        }
        if (!"MULTIPLE_CHOICE".equals(request.getQuestionType().trim()) && answers.size() > 1) {
            throw new IllegalArgumentException("单选题和判断题最多只能有一个答案");
        }
    }

    private void validateConfirmableDraft(
        QuestionDraft draft,
        List<QuestionDraftOption> options,
        List<String> answers
    ) {
        UpdateQuestionDraftRequest request = new UpdateQuestionDraftRequest();
        request.setQuestionType(draft.getQuestionType());
        request.setStem(draft.getStem());
        request.setCorrectAnswer(answers);
        request.setOptions(options.stream().map(option -> {
            CreateQuestionOptionRequest optionRequest = new CreateQuestionOptionRequest();
            optionRequest.setOptionKey(option.getOptionKey());
            optionRequest.setContent(option.getContent());
            optionRequest.setSortOrder(option.getSortOrder());
            return optionRequest;
        }).toList());
        validateDraftUpdateRequest(request);
        if (answers.isEmpty()) {
            throw new IllegalArgumentException("确认入库前必须填写正确答案");
        }
    }

    private void saveDraft(ImportFile importFile, PreparedDraft recognizedQuestion, int sortOrder) {
        QuestionDraft draft = new QuestionDraft();
        draft.setLibraryId(findLibraryId(importFile));
        draft.setImportFileId(importFile.getId());
        draft.setSortOrder(sortOrder);
        draft.setStatus(recognizedQuestion.issueReason() == null ? "WAITING_CONFIRMATION" : "NEEDS_REVIEW");
        draft.setQuestionType(recognizedQuestion.questionType());
        draft.setStem(recognizedQuestion.stem());
        draft.setCorrectAnswer(toJsonOrNull(recognizedQuestion.correctAnswer()));
        draft.setExplanation(recognizedQuestion.explanation());
        draft.setKnowledgePoints(toJsonOrNull(recognizedQuestion.knowledgePoints()));
        draft.setIssueReason(recognizedQuestion.issueReason());
        questionDraftMapper.insert(draft);

        List<RecognizedQuestionOption> options = recognizedQuestion.options();
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

    private List<String> readStringList(String jsonText) {
        if (jsonText == null || jsonText.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(jsonText, new tools.jackson.core.type.TypeReference<List<String>>() {});
        } catch (JacksonException exception) {
            throw new IllegalStateException("题目草稿的正确答案格式错误", exception);
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record PreparedDraft(
        String questionType,
        String stem,
        List<RecognizedQuestionOption> options,
        List<String> correctAnswer,
        String explanation,
        List<String> knowledgePoints,
        String issueReason
    ) {}
}
