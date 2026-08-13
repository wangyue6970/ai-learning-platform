package com.wangyue.backend.dto;

public class QuestionDraftOptionResponse {

    private String optionKey;
    private String content;
    private Integer sortOrder;

    public String getOptionKey() { return optionKey; }
    public void setOptionKey(String optionKey) { this.optionKey = optionKey; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
}
