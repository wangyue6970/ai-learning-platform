package com.wangyue.backend.dto;

import java.util.List;

public class CreateQuestionRequest {

    private Long libraryId;
    private String questionType;
    private String stem;
    private List<String> correctAnswer;
    private String explanation;
    private List<CreateQuestionOptionRequest> options;

    public Long getLibraryId() {
        return libraryId;
    }

    public void setLibraryId(Long libraryId) {
        this.libraryId = libraryId;
    }

    public String getQuestionType() {
        return questionType;
    }

    public void setQuestionType(String questionType) {
        this.questionType = questionType;
    }

    public String getStem() {
        return stem;
    }

    public void setStem(String stem) {
        this.stem = stem;
    }

    public List<String> getCorrectAnswer() {
        return correctAnswer;
    }

    public void setCorrectAnswer(List<String> correctAnswer) {
        this.correctAnswer = correctAnswer;
    }

    public String getExplanation() {
        return explanation;
    }

    public void setExplanation(String explanation) {
        this.explanation = explanation;
    }

    public List<CreateQuestionOptionRequest> getOptions() {
        return options;
    }

    public void setOptions(List<CreateQuestionOptionRequest> options) {
        this.options = options;
    }
}
