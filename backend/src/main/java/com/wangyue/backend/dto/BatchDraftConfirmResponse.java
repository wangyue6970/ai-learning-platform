package com.wangyue.backend.dto;

import java.util.ArrayList;
import java.util.List;

/** 批量确认草稿后的汇总结果；失败草稿保留，方便用户补充后再次确认。 */
public class BatchDraftConfirmResponse {

    private int confirmedCount;
    private final List<FailedDraft> failedDrafts = new ArrayList<>();

    public int getConfirmedCount() {
        return confirmedCount;
    }

    public void addConfirmedDraft() {
        confirmedCount++;
    }

    public List<FailedDraft> getFailedDrafts() {
        return failedDrafts;
    }

    public void addFailedDraft(Long draftId, String message) {
        failedDrafts.add(new FailedDraft(draftId, message));
    }

    public static class FailedDraft {

        private final Long draftId;
        private final String message;

        public FailedDraft(Long draftId, String message) {
            this.draftId = draftId;
            this.message = message;
        }

        public Long getDraftId() {
            return draftId;
        }

        public String getMessage() {
            return message;
        }
    }
}
