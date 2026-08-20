package com.wangyue.backend.dto;

public class ImportFileResponse {

    private Long id;
    private String originalFileName;
    private String status;
    private String errorMessage;
    private Integer totalChunkCount;
    private Integer completedChunkCount;
    private Integer generatedDraftCount;
    private Integer estimatedQuestionCount;
    private Integer needsReviewDraftCount;
    private Boolean wordFastParsed;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getOriginalFileName() { return originalFileName; }
    public void setOriginalFileName(String originalFileName) { this.originalFileName = originalFileName; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public Integer getTotalChunkCount() { return totalChunkCount; }
    public void setTotalChunkCount(Integer totalChunkCount) { this.totalChunkCount = totalChunkCount; }
    public Integer getCompletedChunkCount() { return completedChunkCount; }
    public void setCompletedChunkCount(Integer completedChunkCount) { this.completedChunkCount = completedChunkCount; }
    public Integer getGeneratedDraftCount() { return generatedDraftCount; }
    public void setGeneratedDraftCount(Integer generatedDraftCount) { this.generatedDraftCount = generatedDraftCount; }
    public Integer getEstimatedQuestionCount() { return estimatedQuestionCount; }
    public void setEstimatedQuestionCount(Integer estimatedQuestionCount) { this.estimatedQuestionCount = estimatedQuestionCount; }
    public Integer getNeedsReviewDraftCount() { return needsReviewDraftCount; }
    public void setNeedsReviewDraftCount(Integer needsReviewDraftCount) { this.needsReviewDraftCount = needsReviewDraftCount; }
    public Boolean getWordFastParsed() { return wordFastParsed; }
    public void setWordFastParsed(Boolean wordFastParsed) { this.wordFastParsed = wordFastParsed; }
}
