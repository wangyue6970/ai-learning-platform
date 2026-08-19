package com.wangyue.backend.controller;

import com.wangyue.backend.dto.ApiErrorResponse;
import com.wangyue.backend.exception.AuthenticationException;
import com.wangyue.backend.exception.OperationConflictException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(AuthenticationException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ApiErrorResponse handleAuthenticationFailure(AuthenticationException exception) {
        return new ApiErrorResponse(exception.getMessage(), "下一步：请检查用户名和密码后重新登录。");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiErrorResponse handleIllegalArgument(IllegalArgumentException exception) {
        return new ApiErrorResponse(exception.getMessage(), "下一步：请修改填写内容后重新提交。");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiErrorResponse handleUnreadableRequest() {
        return new ApiErrorResponse("请求内容格式不正确", "下一步：请检查填写格式后重新提交。");
    }

    @ExceptionHandler(OperationConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiErrorResponse handleOperationConflict(OperationConflictException exception) {
        return new ApiErrorResponse(exception.getMessage(), "下一步：请刷新页面后重试。");
    }

    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ApiErrorResponse handleAccessDenied(AccessDeniedException exception) {
        String message = exception.getMessage();
        return new ApiErrorResponse(message != null && message.startsWith("无权")
            ? message
            : "无权访问该数据", "下一步：请返回自己的学习库后重新选择。");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.CONTENT_TOO_LARGE)
    public ApiErrorResponse handleFileTooLarge() {
        return new ApiErrorResponse(
            "单个文件不能超过 20MB，本次总上传不能超过 500MB",
            "下一步：请移除过大的文件后重新选择。"
        );
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiErrorResponse handleUnexpectedException(Exception exception, HttpServletRequest request) {
        logger.error("未处理的服务器异常：{} {}", request.getMethod(), request.getRequestURI(), exception);
        return new ApiErrorResponse("服务暂时异常，请稍后重试", "下一步：请稍后再次尝试；若持续出现，请联系管理员。");
    }
}
