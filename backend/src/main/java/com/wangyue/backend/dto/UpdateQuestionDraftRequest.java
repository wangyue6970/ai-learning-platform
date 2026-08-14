package com.wangyue.backend.dto;

import java.util.List;

/** 用户在确认入库前，对 AI 题目草稿作出的修改。 */
public class UpdateQuestionDraftRequest {

    private String questionType;
    private String stem;
    private List<String> correctAnswer;
    private String explanation;
    private List<String> knowledgePoints;
    private List<CreateQuestionOptionRequest> options;

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
    public List<CreateQuestionOptionRequest> getOptions() { return options; }
    public void setOptions(List<CreateQuestionOptionRequest> options) { this.options = options; }
}
