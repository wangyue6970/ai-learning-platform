package com.wangyue.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("import_file")
public class ImportFile {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long importBatchId;
    private String originalFileName;
    private String storedFilePath;
    private String fileType;
    private Long fileSizeBytes;
    private String status;
    private String errorMessage;
    private String recognitionText;
    private Integer totalChunkCount;
    private Integer completedChunkCount;
    private Integer generatedDraftCount;
    private Integer estimatedQuestionCount;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getImportBatchId() { return importBatchId; }
    public void setImportBatchId(Long importBatchId) { this.importBatchId = importBatchId; }
    public String getOriginalFileName() { return originalFileName; }
    public void setOriginalFileName(String originalFileName) { this.originalFileName = originalFileName; }
    public String getStoredFilePath() { return storedFilePath; }
    public void setStoredFilePath(String storedFilePath) { this.storedFilePath = storedFilePath; }
    public String getFileType() { return fileType; }
    public void setFileType(String fileType) { this.fileType = fileType; }
    public Long getFileSizeBytes() { return fileSizeBytes; }
    public void setFileSizeBytes(Long fileSizeBytes) { this.fileSizeBytes = fileSizeBytes; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public String getRecognitionText() { return recognitionText; }
    public void setRecognitionText(String recognitionText) { this.recognitionText = recognitionText; }
    public Integer getTotalChunkCount() { return totalChunkCount; }
    public void setTotalChunkCount(Integer totalChunkCount) { this.totalChunkCount = totalChunkCount; }
    public Integer getCompletedChunkCount() { return completedChunkCount; }
    public void setCompletedChunkCount(Integer completedChunkCount) { this.completedChunkCount = completedChunkCount; }
    public Integer getGeneratedDraftCount() { return generatedDraftCount; }
    public void setGeneratedDraftCount(Integer generatedDraftCount) { this.generatedDraftCount = generatedDraftCount; }
    public Integer getEstimatedQuestionCount() { return estimatedQuestionCount; }
    public void setEstimatedQuestionCount(Integer estimatedQuestionCount) { this.estimatedQuestionCount = estimatedQuestionCount; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
