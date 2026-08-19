package com.wangyue.backend.dto;

public class ApiErrorResponse {

    private final String message;
    private final String action;

    public ApiErrorResponse(String message) {
        this(message, null);
    }

    public ApiErrorResponse(String message, String action) {
        this.message = message;
        this.action = action;
    }

    public String getMessage() {
        return message;
    }

    public String getAction() {
        return action;
    }
}
