package com.wangyue.backend.exception;

public class AuthenticationException extends RuntimeException {

    public AuthenticationException() {
        super("用户名或密码错误");
    }
}
