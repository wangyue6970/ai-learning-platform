package com.wangyue.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wangyue.backend.dto.ImportBatchResponse;
import com.wangyue.backend.dto.BatchDraftConfirmResponse;
import com.wangyue.backend.dto.ImportFileResponse;
import com.wangyue.backend.dto.QuestionDraftOptionResponse;
import com.wangyue.backend.dto.QuestionDraftResponse;
import com.wangyue.backend.dto.UpdateQuestionDraftRequest;
import com.wangyue.backend.exception.OperationConflictException;
import com.wangyue.backend.entity.ImportBatch;
import com.wangyue.backend.entity.ImportFile;
import com.wangyue.backend.entity.QuestionDraft;
import com.wangyue.backend.entity.QuestionDraftOption;
import com.wangyue.backend.mapper.ImportBatchMapper;
import com.wangyue.backend.mapper.ImportFileMapper;
import com.wangyue.backend.mapper.QuestionDraftMapper;
import com.wangyue.backend.mapper.QuestionDraftOptionMapper;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
public class ImportService {

    private static final int MAX_ERROR_MESSAGE_LENGTH = 480;
    private static final Logger logger = LoggerFactory.getLogger(ImportService.class);

    private final LearningLibraryService learningLibraryService;
    private final ImportBatchMapper importBatchMapper;
    private final ImportFileMapper importFileMapper;
    private final ImportStorageService importStorageService;
    private final OcrService ocrService;
    private final WordDocumentService wordDocumentService;
    private final LlmService llmService;
    private final QuestionDraftService questionDraftService;
    private final QuestionDraftMapper questionDraftMapper;
    private final QuestionDraftOptionMapper questionDraftOptionMapper;
    private final ObjectMapper objectMapper;
    // Keep Word requests deliberately small. Local models understand mixed
    // formats (single choice, judgment, fill-in notes) much better when they
    // only need to structure a few original questions at a time.
    private static final int WORD_LLM_QUESTIONS_PER_CHUNK = 3;
    private static final int WORD_LLM_MAX_CHARACTERS_PER_CHUNK = 4_800;
    private static final Set<String> UNFINISHED_WORD_STATUSES = Set.of(
        "WAITING_RECOGNITION", "RECOGNIZING", "WAITING_STRUCTURING", "STRUCTURING",
        "RECOGNITION_FAILED", "STRUCTURING_FAILED"
    );

    public ImportService(
        LearningLibraryService learningLibraryService,
        ImportBatchMapper importBatchMapper,
        ImportFileMapper importFileMapper,
        ImportStorageService importStorageService,
        OcrService ocrService,
        WordDocumentService wordDocumentService,
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
        this.wordDocumentService = wordDocumentService;
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

        batch.setStatus(hasSuccessfulFile ? "PROCESSING" : "FAILED");
        importBatchMapper.updateById(batch);

        return toResponse(batch);
    }

    /**
     * The mobile client uses this after returning to the import page. File
     * statuses are persisted in MySQL, rather than being kept only in a
     * screen's memory.
     */
    public ImportBatchResponse findLatestBatch(Long libraryId) {
        if (libraryId == null || learningLibraryService.findById(libraryId) == null) {
            throw new IllegalArgumentException("学习库不存在");
        }

        ImportBatch batch = importBatchMapper.selectOne(new LambdaQueryWrapper<ImportBatch>()
            .eq(ImportBatch::getLibraryId, libraryId)
            .orderByDesc(ImportBatch::getId)
            .last("LIMIT 1"));
        return batch == null ? null : toResponse(batch);
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
            return toFileResponse(importFile);
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

    /**
     * Reads a whole import batch for review. This deliberately returns drafts
     * only: a separate explicit confirmation is still required for every
     * formal question.
     */
    public List<QuestionDraftResponse> findBatchDrafts(Long libraryId, Long importBatchId) {
        findOwnedImportBatch(libraryId, importBatchId);

        List<ImportFile> files = importFileMapper.selectList(new LambdaQueryWrapper<ImportFile>()
            .eq(ImportFile::getImportBatchId, importBatchId)
            .orderByAsc(ImportFile::getId));
        if (files.isEmpty()) {
            return List.of();
        }

        List<Long> importFileIds = files.stream().map(ImportFile::getId).toList();
        Map<Long, Integer> fileOrder = new HashMap<>();
        for (int index = 0; index < importFileIds.size(); index++) {
            fileOrder.put(importFileIds.get(index), index);
        }

        return questionDraftMapper.selectList(new LambdaQueryWrapper<QuestionDraft>()
            .in(QuestionDraft::getImportFileId, importFileIds)
        ).stream().sorted(Comparator
            .comparingInt((QuestionDraft draft) -> fileOrder.get(draft.getImportFileId()))
            .thenComparing(QuestionDraft::getSortOrder)
            .thenComparing(QuestionDraft::getId)
        ).map(this::toDraftResponse).toList();
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
        ImportFile importFile = findOwnedImportFile(libraryId, importFileId);
        questionDraftService.confirmDraft(libraryId, importFileId, draftId);

        QuestionDraft draft = questionDraftMapper.selectById(draftId);
        if (draft == null) {
            throw new IllegalArgumentException("题目草稿不存在");
        }
        QuestionDraftResponse response = toDraftResponse(draft);
        cleanUpSourceFileWhenNoDraftNeedsDecision(importFile);
        return response;
    }

    /**
     * A batch is deliberately not one all-or-nothing transaction. Every draft
     * is confirmed through the existing transactional rule, so a malformed
     * draft does not prevent other reviewed drafts from entering the bank.
     */
    public BatchDraftConfirmResponse confirmAllDrafts(Long libraryId, Long importBatchId) {
        findOwnedImportBatch(libraryId, importBatchId);
        BatchDraftConfirmResponse response = new BatchDraftConfirmResponse();
        List<ImportFile> files = importFileMapper.selectList(new LambdaQueryWrapper<ImportFile>()
            .eq(ImportFile::getImportBatchId, importBatchId)
            .orderByAsc(ImportFile::getId));

        for (ImportFile file : files) {
            List<QuestionDraft> waitingDrafts = questionDraftMapper.selectList(new LambdaQueryWrapper<QuestionDraft>()
                .eq(QuestionDraft::getImportFileId, file.getId())
                .eq(QuestionDraft::getStatus, "WAITING_CONFIRMATION")
                .orderByAsc(QuestionDraft::getSortOrder)
                .orderByAsc(QuestionDraft::getId));

            for (QuestionDraft draft : waitingDrafts) {
                try {
                    questionDraftService.confirmDraft(libraryId, file.getId(), draft.getId());
                    response.addConfirmedDraft();
                } catch (IllegalArgumentException | IllegalStateException exception) {
                    response.addFailedDraft(draft.getId(), exception.getMessage());
                }
            }

            if (!waitingDrafts.isEmpty()) {
                cleanUpSourceFileWhenNoDraftNeedsDecision(file);
            }
        }
        return response;
    }

    public void discardDraft(Long libraryId, Long importFileId, Long draftId) {
        ImportFile importFile = findOwnedImportFile(libraryId, importFileId);
        questionDraftService.discardDraft(libraryId, importFileId, draftId);
        cleanUpSourceFileWhenNoDraftNeedsDecision(importFile);
    }

    /**
     * One source document can produce several drafts. It must remain available
     * until the user has processed every draft; then remove every temporary
     * import record and the original file. Formal Question records are kept.
     */
    private void cleanUpSourceFileWhenNoDraftNeedsDecision(ImportFile importFile) {
        boolean hasDraftWaitingForDecision = questionDraftMapper.selectCount(new LambdaQueryWrapper<QuestionDraft>()
            .eq(QuestionDraft::getImportFileId, importFile.getId())
            .in(QuestionDraft::getStatus, List.of("WAITING_CONFIRMATION", "NEEDS_REVIEW"))) > 0;
        if (hasDraftWaitingForDecision) {
            return;
        }

        try {
            importStorageService.deleteStoredFile(importFile.getStoredFilePath());
        } catch (RuntimeException exception) {
            logger.warn("临时导入文件 {} 清理失败", importFile.getId(), exception);
            return;
        }
        Long importBatchId = importFile.getImportBatchId();
        // The database cascade removes AI drafts and draft options; Questions
        // already confirmed into the bank do not reference ImportFile.
        importFileMapper.deleteById(importFile.getId());
        deleteBatchWhenEmpty(importBatchId);
    }

    private void deleteBatchWhenEmpty(Long importBatchId) {
        if (importFileMapper.selectCount(new LambdaQueryWrapper<ImportFile>()
            .eq(ImportFile::getImportBatchId, importBatchId)) == 0) {
            importBatchMapper.deleteById(importBatchId);
        }
    }

    private boolean isWordUpload(MultipartFile file) {
        String originalFileName = file == null ? null : file.getOriginalFilename();
        return originalFileName != null && originalFileName.toLowerCase(Locale.ROOT).endsWith(".docx");
    }

    /** Returns unfinished Word tasks in a library so the queue can cancel them. */
    public List<Long> findUnfinishedWordImportFileIds(Long libraryId, Long currentUserId) {
        requireCurrentUserOwnsLibrary(libraryId, currentUserId);
        return findUnfinishedWordImportFiles(libraryId).stream().map(ImportFile::getId).toList();
    }

    /** Deletes only unfinished Word temporary data; formal questions stay intact. */
    @Transactional
    public void deleteUnfinishedWordImports(Long libraryId, Long currentUserId) {
        requireCurrentUserOwnsLibrary(libraryId, currentUserId);
        for (ImportFile importFile : findUnfinishedWordImportFiles(libraryId)) {
            importStorageService.deleteStoredFile(importFile.getStoredFilePath());
            Long importBatchId = importFile.getImportBatchId();
            importFileMapper.deleteById(importFile.getId());
            deleteBatchWhenEmpty(importBatchId);
        }
    }

    private List<ImportFile> findUnfinishedWordImportFiles(Long libraryId) {
        List<Long> importBatchIds = importBatchMapper.selectList(new LambdaQueryWrapper<ImportBatch>()
            .eq(ImportBatch::getLibraryId, libraryId)
        ).stream().map(ImportBatch::getId).toList();
        if (importBatchIds.isEmpty()) {
            return List.of();
        }
        return importFileMapper.selectList(new LambdaQueryWrapper<ImportFile>()
            .in(ImportFile::getImportBatchId, importBatchIds)
            .in(ImportFile::getStatus, UNFINISHED_WORD_STATUSES)
        ).stream().filter(this::isWordDocument).toList();
    }

    public ImportFileResponse recognizeFile(Long libraryId, Long importFileId) {
        ImportFile importFile = findOwnedImportFile(libraryId, importFileId);
        if (!"WAITING_RECOGNITION".equals(importFile.getStatus())) {
            throw new OperationConflictException("文件状态已变化，请刷新后重试");
        }
        importFile.setStatus("RECOGNIZING");
        importFile.setErrorMessage(null);
        importFileMapper.updateById(importFile);

        try {
            String recognitionText = extractRecognitionText(importFile);
            importFile.setRecognitionText(recognitionText);
            importFile.setStatus("WAITING_STRUCTURING");
            importFileMapper.updateById(importFile);
        } catch (RuntimeException exception) {
            logger.error("导入文件 {} 识别失败", importFileId, exception);
            importFile.setStatus("RECOGNITION_FAILED");
            importFile.setErrorMessage(toStoredErrorMessage(exception));
            importFileMapper.updateById(importFile);
        }

        return toFileResponse(importFile);
    }

    private String extractRecognitionText(ImportFile importFile) {
        Path storedFilePath = Path.of(importFile.getStoredFilePath());
        if (isWordDocument(importFile)) {
            return wordDocumentService.extractText(storedFilePath);
        }
        if (isImageFile(importFile)) {
            return ocrService.recognizeImage(storedFilePath);
        }
        throw new IllegalArgumentException("当前文件不是支持的图片或 .docx Word 文件");
    }

    private boolean isWordDocument(ImportFile importFile) {
        String originalFileName = importFile.getOriginalFileName();
        return originalFileName != null && originalFileName.toLowerCase(Locale.ROOT).endsWith(".docx");
    }

    private boolean isImageFile(ImportFile importFile) {
        return importFile.getFileType() != null && importFile.getFileType().startsWith("image/");
    }

    public ImportFileResponse structureFile(Long libraryId, Long importFileId) {
        ImportFile importFile = findOwnedImportFile(libraryId, importFileId);
        if (!"WAITING_STRUCTURING".equals(importFile.getStatus())) {
            throw new OperationConflictException("文件状态已变化，请刷新后重试");
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
            if (isWordDocument(importFile)) {
                structureWordDocumentInChunks(importFile);
            } else {
                questionDraftService.saveRecognitionResult(
                    importFile.getId(),
                    importFile.getRecognitionText(),
                    llmService.structureQuestions(importFile.getRecognitionText())
                );
            }
            importFile.setStatus("WAITING_CONFIRMATION");
            importFile.setErrorMessage(null);
        } catch (RuntimeException exception) {
            logger.error("导入文件 {} 生成题目失败", importFileId, exception);
            if (generatedDraftCount(importFile) > 0) {
                importFile.setStatus("WAITING_CONFIRMATION");
                importFile.setErrorMessage(buildPartialGenerationMessage(importFile, exception));
            } else {
                importFile.setStatus("STRUCTURING_FAILED");
                importFile.setErrorMessage(toStoredErrorMessage(exception));
            }
        }

        importFileMapper.updateById(importFile);

        return toFileResponse(importFile);
    }

    /**
     * A failed file keeps its uploaded source and recognised Word text. Reset
     * only its processing state so the user can retry without uploading again.
     */
    public ImportFileResponse retryFailedFile(Long libraryId, Long importFileId) {
        ImportFile importFile = findOwnedImportFile(libraryId, importFileId);

        if ("STRUCTURING_FAILED".equals(importFile.getStatus())) {
            if (importFile.getRecognitionText() == null || importFile.getRecognitionText().isBlank()) {
                throw new IllegalStateException("没有可重新生成的识别文字，请重新上传文件");
            }
            importFile.setStatus("WAITING_STRUCTURING");
            importFile.setTotalChunkCount(0);
            importFile.setCompletedChunkCount(0);
            importFile.setGeneratedDraftCount(0);
            importFile.setEstimatedQuestionCount(0);
        } else if ("WAITING_CONFIRMATION".equals(importFile.getStatus())
            && importFile.getErrorMessage() != null
            && importFile.getTotalChunkCount() != null
            && importFile.getCompletedChunkCount() != null
            && importFile.getCompletedChunkCount() < importFile.getTotalChunkCount()) {
            // An older partial Word run can continue from its first unfinished
            // chunk. Successful drafts and its progress counters are retained.
            importFile.setStatus("WAITING_STRUCTURING");
        } else if ("RECOGNITION_FAILED".equals(importFile.getStatus())) {
            if (importFile.getStoredFilePath() == null || importFile.getStoredFilePath().isBlank()) {
                throw new IllegalStateException("原文件已不存在，请重新上传文件");
            }
            importFile.setStatus("WAITING_RECOGNITION");
        } else {
            throw new OperationConflictException("当前文件不需要重试，请刷新后查看最新状态");
        }

        importFile.setErrorMessage(null);
        importFileMapper.updateById(importFile);
        refreshBatchStatus(importFile.getImportBatchId());
        return toFileResponse(importFile);
    }

    /**
     * Replaces unconfirmed Word drafts by asking the local AI to understand
     * the original text again. Confirmed or discarded user decisions are
     * deliberately never overwritten.
     */
    @Transactional
    public ImportFileResponse reparseWordFile(Long libraryId, Long importFileId) {
        ImportFile importFile = findOwnedImportFile(libraryId, importFileId);
        if (!isWordDocument(importFile)) {
            throw new IllegalArgumentException("只有 Word 文件可以按新规则重新整理");
        }
        if ("WAITING_RECOGNITION".equals(importFile.getStatus()) || "RECOGNIZING".equals(importFile.getStatus())
            || "WAITING_STRUCTURING".equals(importFile.getStatus()) || "STRUCTURING".equals(importFile.getStatus())) {
            throw new OperationConflictException("文件正在处理中，请等待当前处理结束");
        }

        List<QuestionDraft> drafts = questionDraftMapper.selectList(new LambdaQueryWrapper<QuestionDraft>()
            .eq(QuestionDraft::getImportFileId, importFileId));
        boolean hasUserDecision = drafts.stream().anyMatch(draft ->
            "CONFIRMED".equals(draft.getStatus()) || "DISCARDED".equals(draft.getStatus())
        );
        if (hasUserDecision) {
            throw new OperationConflictException("已有题目被确认入库或不入库，不能自动替换草稿");
        }
        for (QuestionDraft draft : drafts) {
            questionDraftOptionMapper.delete(new LambdaQueryWrapper<QuestionDraftOption>()
                .eq(QuestionDraftOption::getQuestionDraftId, draft.getId()));
        }
        questionDraftMapper.delete(new LambdaQueryWrapper<QuestionDraft>()
            .eq(QuestionDraft::getImportFileId, importFileId));

        importFile.setStatus("WAITING_STRUCTURING");
        importFile.setErrorMessage(null);
        importFile.setTotalChunkCount(0);
        importFile.setCompletedChunkCount(0);
        importFile.setGeneratedDraftCount(0);
        importFile.setEstimatedQuestionCount(0);
        importFileMapper.updateById(importFile);
        refreshBatchStatus(importFile.getImportBatchId());
        return toFileResponse(importFile);
    }

    /**
     * Word text can contain more than the normal A/B/C/D layout, such as
     * judgment marks, fill-in answers and explanations in brackets. Restore
     * the local-AI path for Word so those formats are understood semantically.
     * Each finished chunk is saved immediately, so progress remains visible.
     */
    private void structureWordDocumentInChunks(ImportFile importFile) {
        WordDocumentService.QuestionTextChunks chunks = wordDocumentService.splitQuestionText(
            importFile.getRecognitionText(), WORD_LLM_QUESTIONS_PER_CHUNK, WORD_LLM_MAX_CHARACTERS_PER_CHUNK
        );
        int completedChunkCount = importFile.getCompletedChunkCount() == null ? 0 : importFile.getCompletedChunkCount();
        boolean canResume = completedChunkCount > 0 && completedChunkCount < chunks.chunks().size();
        if (!canResume) {
            completedChunkCount = 0;
            importFile.setCompletedChunkCount(0);
            importFile.setGeneratedDraftCount(0);
        }
        importFile.setTotalChunkCount(chunks.chunks().size());
        importFile.setCompletedChunkCount(completedChunkCount);
        importFile.setEstimatedQuestionCount(chunks.estimatedQuestionCount());
        importFileMapper.updateById(importFile);

        for (int chunkIndex = completedChunkCount; chunkIndex < chunks.chunks().size(); chunkIndex++) {
            int savedCount = questionDraftService.appendRecognitionResult(
                importFile.getId(), llmService.structureQuestions(chunks.chunks().get(chunkIndex)),
                generatedDraftCount(importFile) + 1
            );
            importFile.setGeneratedDraftCount(generatedDraftCount(importFile) + savedCount);
            importFile.setCompletedChunkCount(chunkIndex + 1);
            importFileMapper.updateById(importFile);
        }

        if (generatedDraftCount(importFile) == 0) {
            throw new IllegalStateException("未识别出可确认的题目");
        }
    }

    private int generatedDraftCount(ImportFile importFile) {
        return importFile.getGeneratedDraftCount() == null ? 0 : importFile.getGeneratedDraftCount();
    }

    private String buildPartialGenerationMessage(ImportFile importFile, RuntimeException exception) {
        int totalQuestions = importFile.getTotalChunkCount() == null ? 0 : importFile.getTotalChunkCount();
        int failedQuestion = Math.min(
            (importFile.getCompletedChunkCount() == null ? 0 : importFile.getCompletedChunkCount()) + 1,
            totalQuestions
        );
        String errorMessage = toStoredErrorMessage(exception);
        return "第 " + failedQuestion + "/" + totalQuestions + " 题附近处理失败：" + errorMessage
            + "；已保留 " + generatedDraftCount(importFile) + " 道草稿，可先查看并确认。";
    }

    /** Runs inside the server queue; it never creates a formal question. */
    public void processFileInBackground(Long importFileId) {
        ImportFile importFile = importFileMapper.selectById(importFileId);
        if (importFile == null) {
            return;
        }
        ImportBatch batch = importBatchMapper.selectById(importFile.getImportBatchId());
        if (batch == null) {
            return;
        }

        if ("WAITING_RECOGNITION".equals(importFile.getStatus())) {
            recognizeFile(batch.getLibraryId(), importFileId);
        }

        importFile = importFileMapper.selectById(importFileId);
        if (importFile != null && "WAITING_STRUCTURING".equals(importFile.getStatus())) {
            structureFile(batch.getLibraryId(), importFileId);
        }

        refreshBatchStatus(batch.getId());
    }

    public void refreshBatchStatus(Long importBatchId) {
        ImportBatch batch = importBatchMapper.selectById(importBatchId);
        if (batch == null) {
            return;
        }

        List<ImportFile> files = importFileMapper.selectList(new LambdaQueryWrapper<ImportFile>()
            .eq(ImportFile::getImportBatchId, importBatchId));
        boolean hasProcessingFile = files.stream().anyMatch(file ->
            "WAITING_RECOGNITION".equals(file.getStatus())
                || "RECOGNIZING".equals(file.getStatus())
                || "WAITING_STRUCTURING".equals(file.getStatus())
                || "STRUCTURING".equals(file.getStatus())
        );
        boolean hasReadyDraft = files.stream().anyMatch(file -> "WAITING_CONFIRMATION".equals(file.getStatus()));
        boolean hasFailedFile = files.stream().anyMatch(file ->
            "UPLOAD_FAILED".equals(file.getStatus())
                || "RECOGNITION_FAILED".equals(file.getStatus())
                || "STRUCTURING_FAILED".equals(file.getStatus())
        );

        if (hasProcessingFile) {
            batch.setStatus("PROCESSING");
        } else if (hasReadyDraft && hasFailedFile) {
            batch.setStatus("PARTIAL_SUCCESS");
        } else if (hasReadyDraft) {
            batch.setStatus("WAITING_CONFIRMATION");
        } else if (hasFailedFile) {
            batch.setStatus("FAILED");
        }
        importBatchMapper.updateById(batch);
    }

    /**
     * HTTP entry points call these overloads. The no-user overloads remain
     * available only for the server-owned import processing queue.
     */
    public ImportBatchResponse createBatch(
        Long libraryId, List<MultipartFile> files, Long currentUserId
    ) {
        requireCurrentUserOwnsLibrary(libraryId, currentUserId);
        return createBatch(libraryId, files);
    }

    public ImportBatchResponse findLatestBatch(Long libraryId, Long currentUserId) {
        requireCurrentUserOwnsLibrary(libraryId, currentUserId);
        return findLatestBatch(libraryId);
    }

    public List<QuestionDraftResponse> findDrafts(
        Long libraryId, Long importFileId, Long currentUserId
    ) {
        requireCurrentUserOwnsLibrary(libraryId, currentUserId);
        return findDrafts(libraryId, importFileId);
    }

    public List<QuestionDraftResponse> findBatchDrafts(
        Long libraryId, Long importBatchId, Long currentUserId
    ) {
        requireCurrentUserOwnsLibrary(libraryId, currentUserId);
        return findBatchDrafts(libraryId, importBatchId);
    }

    public QuestionDraftResponse updateDraft(
        Long libraryId,
        Long importFileId,
        Long draftId,
        UpdateQuestionDraftRequest request,
        Long currentUserId
    ) {
        requireCurrentUserOwnsLibrary(libraryId, currentUserId);
        return updateDraft(libraryId, importFileId, draftId, request);
    }

    public QuestionDraftResponse confirmDraft(
        Long libraryId, Long importFileId, Long draftId, Long currentUserId
    ) {
        requireCurrentUserOwnsLibrary(libraryId, currentUserId);
        return confirmDraft(libraryId, importFileId, draftId);
    }

    public BatchDraftConfirmResponse confirmAllDrafts(
        Long libraryId, Long importBatchId, Long currentUserId
    ) {
        requireCurrentUserOwnsLibrary(libraryId, currentUserId);
        return confirmAllDrafts(libraryId, importBatchId);
    }

    public void discardDraft(Long libraryId, Long importFileId, Long draftId, Long currentUserId) {
        requireCurrentUserOwnsLibrary(libraryId, currentUserId);
        discardDraft(libraryId, importFileId, draftId);
    }

    public ImportFileResponse recognizeFile(Long libraryId, Long importFileId, Long currentUserId) {
        requireCurrentUserOwnsLibrary(libraryId, currentUserId);
        return recognizeFile(libraryId, importFileId);
    }

    public ImportFileResponse structureFile(Long libraryId, Long importFileId, Long currentUserId) {
        requireCurrentUserOwnsLibrary(libraryId, currentUserId);
        return structureFile(libraryId, importFileId);
    }

    public ImportFileResponse retryFailedFile(Long libraryId, Long importFileId, Long currentUserId) {
        requireCurrentUserOwnsLibrary(libraryId, currentUserId);
        return retryFailedFile(libraryId, importFileId);
    }

    public ImportFileResponse reparseWordFile(Long libraryId, Long importFileId, Long currentUserId) {
        requireCurrentUserOwnsLibrary(libraryId, currentUserId);
        return reparseWordFile(libraryId, importFileId);
    }

    private void requireCurrentUserOwnsLibrary(Long libraryId, Long currentUserId) {
        learningLibraryService.findOwnedById(libraryId, currentUserId);
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

    private ImportBatch findOwnedImportBatch(Long libraryId, Long importBatchId) {
        ImportBatch batch = importBatchMapper.selectById(importBatchId);
        if (batch == null || !libraryId.equals(batch.getLibraryId())) {
            throw new IllegalArgumentException("导入批次不属于当前学习库");
        }
        return batch;
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
        response.setIssueReason(draft.getIssueReason());
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
        response.setTotalChunkCount(importFile.getTotalChunkCount());
        response.setCompletedChunkCount(importFile.getCompletedChunkCount());
        response.setGeneratedDraftCount(importFile.getGeneratedDraftCount());
        response.setEstimatedQuestionCount(importFile.getEstimatedQuestionCount());
        response.setNeedsReviewDraftCount(Math.toIntExact(questionDraftMapper.selectCount(
            new LambdaQueryWrapper<QuestionDraft>()
                .eq(QuestionDraft::getImportFileId, importFile.getId())
                .eq(QuestionDraft::getStatus, "NEEDS_REVIEW")
        )));
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
