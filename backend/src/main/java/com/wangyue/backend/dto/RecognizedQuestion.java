package com.wangyue.backend.dto;

import java.util.List;

/**
 * Java 与 LLM 的输出合同：LLM 只能返回这些字段，不能直接操作数据库。
 */
public class RecognizedQuestion {

    private String questionType;
    private String stem;
    private List<RecognizedQuestionOption> options;
    private List<String> correctAnswer;
    private String explanation;
    private List<String> knowledgePoints;

    public String getQuestionType() { return questionType; }
    public void setQuestionType(String questionType) { this.questionType = questionType; }
    public String getStem() { return stem; }
    public void setStem(String stem) { this.stem = stem; }
    public List<RecognizedQuestionOption> getOptions() { return options; }
    public void setOptions(List<RecognizedQuestionOption> options) { this.options = options; }
    public List<String> getCorrectAnswer() { return correctAnswer; }
    public void setCorrectAnswer(List<String> correctAnswer) { this.correctAnswer = correctAnswer; }
    public String getExplanation() { return explanation; }
    public void setExplanation(String explanation) { this.explanation = explanation; }
    public List<String> getKnowledgePoints() { return knowledgePoints; }
    public void setKnowledgePoints(List<String> knowledgePoints) { this.knowledgePoints = knowledgePoints; }
}
