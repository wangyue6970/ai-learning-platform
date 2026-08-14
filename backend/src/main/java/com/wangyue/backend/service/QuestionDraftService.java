package com.wangyue.backend.service;

import com.wangyue.backend.dto.RecognizedQuestion;
import com.wangyue.backend.dto.RecognizedQuestionOption;
import com.wangyue.backend.dto.CreateQuestionOptionRequest;
import com.wangyue.backend.dto.UpdateQuestionDraftRequest;
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
        if (!"WAITING_CONFIRMATION".equals(draft.getStatus())) {
            throw new IllegalStateException("当前题目草稿不能再编辑");
        }

        validateDraftUpdateRequest(request);
        draft.setQuestionType(request.getQuestionType().trim());
        draft.setStem(request.getStem().trim());
        draft.setCorrectAnswer(toJsonOrNull(request.getCorrectAnswer()));
        draft.setExplanation(blankToNull(request.getExplanation()));
        draft.setKnowledgePoints(toJsonOrNull(request.getKnowledgePoints()));
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
            throw new IllegalStateException("当前题目草稿已经确认入库，不能重复确认");
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
}
