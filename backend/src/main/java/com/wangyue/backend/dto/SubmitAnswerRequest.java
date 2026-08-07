package com.wangyue.backend.dto;

import java.util.List;

public class SubmitAnswerRequest {

    private Long libraryId;
    private Long questionId;
    private List<String> selectedAnswer;

    public Long getLibraryId() {
        return libraryId;
    }

    public void setLibraryId(Long libraryId) {
        this.libraryId = libraryId;
    }

    public Long getQuestionId() {
        return questionId;
    }

    public void setQuestionId(Long questionId) {
        this.questionId = questionId;
    }

    public List<String> getSelectedAnswer() {
        return selectedAnswer;
    }

    public void setSelectedAnswer(List<String> selectedAnswer) {
        this.selectedAnswer = selectedAnswer;
    }
}
