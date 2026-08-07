package com.wangyue.backend.dto;

import java.util.List;

public class SubmitAnswerResponse {

    private Boolean correct;
    private List<String> correctAnswer;
    private String explanation;
    private Integer consecutiveCorrectCount;
    private Boolean removedFromWrongQuestions;

    public Boolean getCorrect() {
        return correct;
    }

    public void setCorrect(Boolean correct) {
        this.correct = correct;
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

    public Integer getConsecutiveCorrectCount() {
        return consecutiveCorrectCount;
    }

    public void setConsecutiveCorrectCount(Integer consecutiveCorrectCount) {
        this.consecutiveCorrectCount = consecutiveCorrectCount;
    }

    public Boolean getRemovedFromWrongQuestions() {
        return removedFromWrongQuestions;
    }

    public void setRemovedFromWrongQuestions(Boolean removedFromWrongQuestions) {
        this.removedFromWrongQuestions = removedFromWrongQuestions;
    }
}
