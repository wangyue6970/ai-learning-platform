package com.wangyue.backend.dto;

/** LLM 从原文提取出的一个选项，还不是正式题目选项。 */
public class RecognizedQuestionOption {

    private String optionKey;
    private String content;

    public String getOptionKey() { return optionKey; }
    public void setOptionKey(String optionKey) { this.optionKey = optionKey; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
}
