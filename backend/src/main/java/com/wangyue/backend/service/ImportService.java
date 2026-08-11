package com.wangyue.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wangyue.backend.dto.ImportBatchResponse;
import com.wangyue.backend.dto.ImportFileResponse;
import com.wangyue.backend.entity.ImportBatch;
import com.wangyue.backend.entity.ImportFile;
import com.wangyue.backend.mapper.ImportBatchMapper;
import com.wangyue.backend.mapper.ImportFileMapper;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ImportService {

    private final LearningLibraryService learningLibraryService;
    private final ImportBatchMapper importBatchMapper;
    private final ImportFileMapper importFileMapper;
    private final ImportStorageService importStorageService;

    public ImportService(
        LearningLibraryService learningLibraryService,
        ImportBatchMapper importBatchMapper,
        ImportFileMapper importFileMapper,
        ImportStorageService importStorageService
    ) {
        this.learningLibraryService = learningLibraryService;
        this.importBatchMapper = importBatchMapper;
        this.importFileMapper = importFileMapper;
        this.importStorageService = importStorageService;
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
}
