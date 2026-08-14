package com.wangyue.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wangyue.backend.dto.ImportBatchResponse;
import com.wangyue.backend.dto.ImportFileResponse;
import com.wangyue.backend.dto.QuestionDraftOptionResponse;
import com.wangyue.backend.dto.QuestionDraftResponse;
import com.wangyue.backend.dto.UpdateQuestionDraftRequest;
import com.wangyue.backend.entity.ImportBatch;
import com.wangyue.backend.entity.ImportFile;
import com.wangyue.backend.entity.QuestionDraft;
import com.wangyue.backend.entity.QuestionDraftOption;
import com.wangyue.backend.mapper.ImportBatchMapper;
import com.wangyue.backend.mapper.ImportFileMapper;
import com.wangyue.backend.mapper.QuestionDraftMapper;
import com.wangyue.backend.mapper.QuestionDraftOptionMapper;
import java.nio.file.Path;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
public class ImportService {

    private static final int MAX_ERROR_MESSAGE_LENGTH = 480;

    private final LearningLibraryService learningLibraryService;
    private final ImportBatchMapper importBatchMapper;
    private final ImportFileMapper importFileMapper;
    private final ImportStorageService importStorageService;
    private final OcrService ocrService;
    private final LlmService llmService;
    private final QuestionDraftService questionDraftService;
    private final QuestionDraftMapper questionDraftMapper;
    private final QuestionDraftOptionMapper questionDraftOptionMapper;
    private final ObjectMapper objectMapper;

    public ImportService(
        LearningLibraryService learningLibraryService,
        ImportBatchMapper importBatchMapper,
        ImportFileMapper importFileMapper,
        ImportStorageService importStorageService,
        OcrService ocrService,
        LlmService llmService,
        QuestionDraftService questionDraftService,
        QuestionDraftMapper questionDraftMapper,
        QuestionDraftOptionMapper questionDraftOptionMapper,
        ObjectMapper objectMapper
    ) {
        this.learningLibraryService = learningLibraryService;
        this.importBatchMapper = importBatchMapper;
        this.importFileMapper = importFileMapper;
        this.importStorageService = importStorageService;
        this.ocrService = ocrService;
        this.llmService = llmService;
        this.questionDraftService = questionDraftService;
        this.questionDraftMapper = questionDraftMapper;
        this.questionDraftOptionMapper = questionDraftOptionMapper;
        this.objectMapper = objectMapper;
    }

    public ImportBatchResponse createBatch(Long libraryId, List<MultipartFile> files) {
        if (libraryId == null || learningLibraryService.findById(libraryId) == null) {
            throw new IllegalArgumentException("学习库不存在");
        }
        if (files == null || files.isEmpty()) {
            throw new IllegalArgumentException("请至少选择一个文件");
        }

        ImportBatch batch = new ImportBatch();
        batch.setLibraryId(libraryId);
        batch.setStatus("UPLOADING");
        importBatchMapper.insert(batch);

        boolean hasSuccessfulFile = false;
        for (MultipartFile file : files) {
            if (saveOneFile(batch.getId(), file)) {
                hasSuccessfulFile = true;
            }
        }

        batch.setStatus(hasSuccessfulFile ? "WAITING_RECOGNITION" : "UPLOAD_FAILED");
        importBatchMapper.updateById(batch);

        return toResponse(batch);
    }

    private boolean saveOneFile(Long batchId, MultipartFile file) {
        ImportFile importFile = new ImportFile();
        importFile.setImportBatchId(batchId);
        importFile.setOriginalFileName(file == null || file.getOriginalFilename() == null
            ? "未命名文件" : file.getOriginalFilename());
        importFile.setFileType(file == null || file.getContentType() == null
            ? "unknown" : file.getContentType());
        importFile.setFileSizeBytes(file == null ? 0L : file.getSize());

        try {
            importFile.setStoredFilePath(importStorageService.store(file));
            importFile.setStatus("WAITING_RECOGNITION");
        } catch (IllegalArgumentException | IllegalStateException exception) {
            importFile.setStatus("UPLOAD_FAILED");
            importFile.setErrorMessage(exception.getMessage());
        }

        importFileMapper.insert(importFile);
        return "WAITING_RECOGNITION".equals(importFile.getStatus());
    }

    private ImportBatchResponse toResponse(ImportBatch batch) {
        List<ImportFileResponse> files = importFileMapper.selectList(
            new LambdaQueryWrapper<ImportFile>()
                .eq(ImportFile::getImportBatchId, batch.getId())
                .orderByAsc(ImportFile::getId)
        ).stream().map(importFile -> {
            ImportFileResponse response = new ImportFileResponse();
            response.setId(importFile.getId());
            response.setOriginalFileName(importFile.getOriginalFileName());
            response.setStatus(importFile.getStatus());
            response.setErrorMessage(importFile.getErrorMessage());
            return response;
        }).toList();

        ImportBatchResponse response = new ImportBatchResponse();
        response.setId(batch.getId());
        response.setLibraryId(batch.getLibraryId());
        response.setStatus(batch.getStatus());
        response.setFiles(files);
        return response;
    }

    public List<QuestionDraftResponse> findDrafts(Long libraryId, Long importFileId) {
        findOwnedImportFile(libraryId, importFileId);

        return questionDraftMapper.selectList(new LambdaQueryWrapper<QuestionDraft>()
            .eq(QuestionDraft::getImportFileId, importFileId)
            .orderByAsc(QuestionDraft::getSortOrder)
            .orderByAsc(QuestionDraft::getId)
        ).stream().map(this::toDraftResponse).toList();
    }

    public QuestionDraftResponse updateDraft(
        Long libraryId,
        Long importFileId,
        Long draftId,
        UpdateQuestionDraftRequest request
    ) {
        findOwnedImportFile(libraryId, importFileId);
        questionDraftService.updateDraft(libraryId, importFileId, draftId, request);

        QuestionDraft draft = questionDraftMapper.selectById(draftId);
        if (draft == null) {
            throw new IllegalArgumentException("题目草稿不存在");
        }
        return toDraftResponse(draft);
    }

    public QuestionDraftResponse confirmDraft(Long libraryId, Long importFileId, Long draftId) {
        findOwnedImportFile(libraryId, importFileId);
        questionDraftService.confirmDraft(libraryId, importFileId, draftId);

        QuestionDraft draft = questionDraftMapper.selectById(draftId);
        if (draft == null) {
            throw new IllegalArgumentException("题目草稿不存在");
        }
        return toDraftResponse(draft);
    }

    public ImportFileResponse recognizeFile(Long libraryId, Long importFileId) {
        ImportFile importFile = findOwnedImportFile(libraryId, importFileId);
        if (!"WAITING_RECOGNITION".equals(importFile.getStatus())) {
            throw new IllegalStateException("当前文件不处于等待识别状态");
        }
        if (importFile.getFileType() == null || !importFile.getFileType().startsWith("image/")) {
            throw new IllegalArgumentException("当前文件不是图片，不能使用图片识别");
        }

        importFile.setStatus("RECOGNIZING");
        importFile.setErrorMessage(null);
        importFileMapper.updateById(importFile);

        try {
            String recognitionText = ocrService.recognizeImage(Path.of(importFile.getStoredFilePath()));
            importFile.setRecognitionText(recognitionText);
            importFile.setStatus("WAITING_STRUCTURING");
            importFileMapper.updateById(importFile);
        } catch (RuntimeException exception) {
            importFile.setStatus("RECOGNITION_FAILED");
            importFile.setErrorMessage(toStoredErrorMessage(exception));
            importFileMapper.updateById(importFile);
        }

        return toFileResponse(importFile);
    }

    public ImportFileResponse structureFile(Long libraryId, Long importFileId) {
        ImportFile importFile = findOwnedImportFile(libraryId, importFileId);
        if (!"WAITING_STRUCTURING".equals(importFile.getStatus())) {
            throw new IllegalStateException("当前文件不处于等待生成题目状态");
        }
        if (importFile.getRecognitionText() == null || importFile.getRecognitionText().isBlank()) {
            importFile.setStatus("STRUCTURING_FAILED");
            importFile.setErrorMessage("没有可生成题目的识别文字");
            importFileMapper.updateById(importFile);
            return toFileResponse(importFile);
        }

        importFile.setStatus("STRUCTURING");
        importFile.setErrorMessage(null);
        importFileMapper.updateById(importFile);

        try {
            questionDraftService.saveRecognitionResult(
                importFile.getId(),
                importFile.getRecognitionText(),
                llmService.structureQuestions(importFile.getRecognitionText())
            );
            importFile.setStatus("WAITING_CONFIRMATION");
        } catch (RuntimeException exception) {
            importFile.setStatus("STRUCTURING_FAILED");
            importFile.setErrorMessage(toStoredErrorMessage(exception));
            importFileMapper.updateById(importFile);
        }

        return toFileResponse(importFile);
    }

    private ImportFile findOwnedImportFile(Long libraryId, Long importFileId) {
        ImportFile importFile = importFileMapper.selectById(importFileId);
        if (importFile == null) {
            throw new IllegalArgumentException("导入文件不存在");
        }

        ImportBatch batch = importBatchMapper.selectById(importFile.getImportBatchId());
        if (batch == null || !libraryId.equals(batch.getLibraryId())) {
            throw new IllegalArgumentException("导入文件不属于当前学习库");
        }
        return importFile;
    }

    private QuestionDraftResponse toDraftResponse(QuestionDraft draft) {
        QuestionDraftResponse response = new QuestionDraftResponse();
        response.setId(draft.getId());
        response.setImportFileId(draft.getImportFileId());
        response.setSortOrder(draft.getSortOrder());
        response.setStatus(draft.getStatus());
        response.setQuestionType(draft.getQuestionType());
        response.setStem(draft.getStem());
        response.setCorrectAnswer(readStringList(draft.getCorrectAnswer()));
        response.setExplanation(draft.getExplanation());
        response.setKnowledgePoints(readStringList(draft.getKnowledgePoints()));
        response.setOptions(questionDraftOptionMapper.selectList(
            new LambdaQueryWrapper<QuestionDraftOption>()
                .eq(QuestionDraftOption::getQuestionDraftId, draft.getId())
                .orderByAsc(QuestionDraftOption::getSortOrder)
        ).stream().map(option -> {
            QuestionDraftOptionResponse optionResponse = new QuestionDraftOptionResponse();
            optionResponse.setOptionKey(option.getOptionKey());
            optionResponse.setContent(option.getContent());
            optionResponse.setSortOrder(option.getSortOrder());
            return optionResponse;
        }).toList());
        return response;
    }

    private ImportFileResponse toFileResponse(ImportFile importFile) {
        ImportFileResponse response = new ImportFileResponse();
        response.setId(importFile.getId());
        response.setOriginalFileName(importFile.getOriginalFileName());
        response.setStatus(importFile.getStatus());
        response.setErrorMessage(importFile.getErrorMessage());
        return response;
    }

    private String toStoredErrorMessage(RuntimeException exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return "图片识别失败，请稍后重试";
        }
        return message.length() <= MAX_ERROR_MESSAGE_LENGTH
            ? message
            : message.substring(0, MAX_ERROR_MESSAGE_LENGTH - 1) + "…";
    }

    private List<String> readStringList(String jsonText) {
        if (jsonText == null || jsonText.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(jsonText, new TypeReference<List<String>>() {});
        } catch (JacksonException exception) {
            throw new IllegalStateException("识别草稿的数据格式错误", exception);
        }
    }
}
