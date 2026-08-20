package com.wangyue.backend.controller;

import com.wangyue.backend.dto.ImportBatchResponse;
import com.wangyue.backend.dto.BatchDraftConfirmResponse;
import com.wangyue.backend.dto.ImportFileResponse;
import com.wangyue.backend.dto.QuestionDraftResponse;
import com.wangyue.backend.dto.UpdateQuestionDraftRequest;
import com.wangyue.backend.service.ImportProcessingQueue;
import com.wangyue.backend.service.ImportService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/libraries/{libraryId}/import-batches")
public class ImportController {

    private final ImportService importService;
    private final ImportProcessingQueue importProcessingQueue;

    public ImportController(ImportService importService, ImportProcessingQueue importProcessingQueue) {
        this.importService = importService;
        this.importProcessingQueue = importProcessingQueue;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ImportBatchResponse create(
        @PathVariable Long libraryId,
        @RequestParam("files") List<MultipartFile> files,
        @AuthenticationPrincipal Long currentUserId
    ) {
        ImportBatchResponse batch = importService.createBatch(libraryId, files, currentUserId);
        importProcessingQueue.enqueueBatch(batch.getId());
        return batch;
    }

    @GetMapping("/latest")
    public ImportBatchResponse findLatest(
        @PathVariable Long libraryId,
        @AuthenticationPrincipal Long currentUserId
    ) {
        return importService.findLatestBatch(libraryId, currentUserId);
    }

    /**
     * Returns every draft in one batch. The client uses this for the batch
     * review screen; it does not confirm or create any formal questions.
     */
    @GetMapping("/{importBatchId}/drafts")
    public List<QuestionDraftResponse> findBatchDrafts(
        @PathVariable Long libraryId,
        @PathVariable Long importBatchId,
        @AuthenticationPrincipal Long currentUserId
    ) {
        return importService.findBatchDrafts(libraryId, importBatchId, currentUserId);
    }

    @PostMapping("/{importBatchId}/drafts/confirm-all")
    public BatchDraftConfirmResponse confirmAllDrafts(
        @PathVariable Long libraryId,
        @PathVariable Long importBatchId,
        @AuthenticationPrincipal Long currentUserId
    ) {
        return importService.confirmAllDrafts(libraryId, importBatchId, currentUserId);
    }

    @GetMapping("/files/{importFileId}/drafts")
    public List<QuestionDraftResponse> findDrafts(
        @PathVariable Long libraryId,
        @PathVariable Long importFileId,
        @AuthenticationPrincipal Long currentUserId
    ) {
        return importService.findDrafts(libraryId, importFileId, currentUserId);
    }

    @PatchMapping("/files/{importFileId}/drafts/{draftId}")
    public QuestionDraftResponse updateDraft(
        @PathVariable Long libraryId,
        @PathVariable Long importFileId,
        @PathVariable Long draftId,
        @RequestBody UpdateQuestionDraftRequest request,
        @AuthenticationPrincipal Long currentUserId
    ) {
        return importService.updateDraft(libraryId, importFileId, draftId, request, currentUserId);
    }

    @DeleteMapping("/files/{importFileId}/drafts/{draftId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void discardDraft(
        @PathVariable Long libraryId,
        @PathVariable Long importFileId,
        @PathVariable Long draftId,
        @AuthenticationPrincipal Long currentUserId
    ) {
        importService.discardDraft(libraryId, importFileId, draftId, currentUserId);
    }

    @PostMapping("/files/{importFileId}/drafts/{draftId}/confirm")
    public QuestionDraftResponse confirmDraft(
        @PathVariable Long libraryId,
        @PathVariable Long importFileId,
        @PathVariable Long draftId,
        @AuthenticationPrincipal Long currentUserId
    ) {
        return importService.confirmDraft(libraryId, importFileId, draftId, currentUserId);
    }

    @PostMapping("/files/{importFileId}/recognize")
    public ImportFileResponse recognize(
        @PathVariable Long libraryId,
        @PathVariable Long importFileId,
        @AuthenticationPrincipal Long currentUserId
    ) {
        return importService.recognizeFile(libraryId, importFileId, currentUserId);
    }

    @PostMapping("/files/{importFileId}/structure")
    public ImportFileResponse structure(
        @PathVariable Long libraryId,
        @PathVariable Long importFileId,
        @AuthenticationPrincipal Long currentUserId
    ) {
        return importService.structureFile(libraryId, importFileId, currentUserId);
    }

    @PostMapping("/files/{importFileId}/retry")
    public ImportFileResponse retry(
        @PathVariable Long libraryId,
        @PathVariable Long importFileId,
        @AuthenticationPrincipal Long currentUserId
    ) {
        ImportFileResponse file = importService.retryFailedFile(libraryId, importFileId, currentUserId);
        importProcessingQueue.enqueueFile(importFileId);
        return file;
    }

    @PostMapping("/files/{importFileId}/reparse-word")
    public ImportFileResponse reparseWord(
        @PathVariable Long libraryId,
        @PathVariable Long importFileId,
        @AuthenticationPrincipal Long currentUserId
    ) {
        ImportFileResponse file = importService.reparseWordFile(libraryId, importFileId, currentUserId);
        importProcessingQueue.enqueueFile(importFileId);
        return file;
    }
}
