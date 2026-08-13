package com.wangyue.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/** AI 临时草稿中的一个选项，例如 A、B、C、D。 */
@TableName("question_draft_option")
public class QuestionDraftOption {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long questionDraftId;
    private String optionKey;
    private String content;
    private Integer sortOrder;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getQuestionDraftId() { return questionDraftId; }
    public void setQuestionDraftId(Long questionDraftId) { this.questionDraftId = questionDraftId; }
    public String getOptionKey() { return optionKey; }
    public void setOptionKey(String optionKey) { this.optionKey = optionKey; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
}
