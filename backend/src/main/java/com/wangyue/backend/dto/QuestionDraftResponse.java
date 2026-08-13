package com.wangyue.backend.dto;

import java.util.List;

/** 供确认页面显示的一道临时识别题目。 */
public class QuestionDraftResponse {

    private Long id;
    private Long importFileId;
    private Integer sortOrder;
    private String status;
    private String questionType;
    private String stem;
    private List<String> correctAnswer;
    private String explanation;
    private List<String> knowledgePoints;
    private List<QuestionDraftOptionResponse> options;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
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
    public List<String> getCorrectAnswer() { return correctAnswer; }
    public void setCorrectAnswer(List<String> correctAnswer) { this.correctAnswer = correctAnswer; }
    public String getExplanation() { return explanation; }
    public void setExplanation(String explanation) { this.explanation = explanation; }
    public List<String> getKnowledgePoints() { return knowledgePoints; }
    public void setKnowledgePoints(List<String> knowledgePoints) { this.knowledgePoints = knowledgePoints; }
    public List<QuestionDraftOptionResponse> getOptions() { return options; }
    public void setOptions(List<QuestionDraftOptionResponse> options) { this.options = options; }
}
