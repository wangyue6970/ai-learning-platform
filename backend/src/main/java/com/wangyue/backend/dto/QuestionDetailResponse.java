package com.wangyue.backend.dto;

import java.util.List;

public class QuestionDetailResponse {

    private Long id;
    private Long libraryId;
    private String questionType;
    private String stem;
    private List<QuestionOptionResponse> options;
    private List<String> correctAnswer;
    private String explanation;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getLibraryId() { return libraryId; }
    public void setLibraryId(Long libraryId) { this.libraryId = libraryId; }
    public String getQuestionType() { return questionType; }
    public void setQuestionType(String questionType) { this.questionType = questionType; }
    public String getStem() { return stem; }
    public void setStem(String stem) { this.stem = stem; }
    public List<QuestionOptionResponse> getOptions() { return options; }
    public void setOptions(List<QuestionOptionResponse> options) { this.options = options; }
    public List<String> getCorrectAnswer() { return correctAnswer; }
    public void setCorrectAnswer(List<String> correctAnswer) { this.correctAnswer = correctAnswer; }
    public String getExplanation() { return explanation; }
    public void setExplanation(String explanation) { this.explanation = explanation; }
}
