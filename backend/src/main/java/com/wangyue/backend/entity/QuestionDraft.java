package com.wangyue.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

/**
 * AI 识别出的临时题目。只有用户确认后，才会被转换为正式 Question。
 */
@TableName("question_draft")
public class QuestionDraft {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long libraryId;
    private Long importFileId;
    private Integer sortOrder;
    private String status;
    private String questionType;
    private String stem;
    private String correctAnswer;
    private String explanation;
    private String knowledgePoints;
    private String issueReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getLibraryId() { return libraryId; }
    public void setLibraryId(Long libraryId) { this.libraryId = libraryId; }
    public Long getImportFileId() { return importFileId; }
    public void setImportFileId(Long importFileId) { this.importFileId = importFileId; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getQuestionType() { return questionType; }
    public void setQuestionType(String questionType) { this.questionType = questionType; }
    public String getStem() { return stem; }
    public void setStem(String stem) { this.stem = stem; }
    public String getCorrectAnswer() { return correctAnswer; }
    public void setCorrectAnswer(String correctAnswer) { this.correctAnswer = correctAnswer; }
    public String getExplanation() { return explanation; }
    public void setExplanation(String explanation) { this.explanation = explanation; }
    public String getKnowledgePoints() { return knowledgePoints; }
    public void setKnowledgePoints(String knowledgePoints) { this.knowledgePoints = knowledgePoints; }
    public String getIssueReason() { return issueReason; }
    public void setIssueReason(String issueReason) { this.issueReason = issueReason; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
