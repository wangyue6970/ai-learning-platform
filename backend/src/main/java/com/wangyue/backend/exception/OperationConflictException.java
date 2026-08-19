package com.wangyue.backend.exception;

/**
 * 用户重复提交或页面状态已经变化时使用。
 * 这不是服务器故障，前端应提示用户刷新后重试。
 */
public class OperationConflictException extends IllegalStateException {

    public OperationConflictException(String message) {
        super(message);
    }
}
