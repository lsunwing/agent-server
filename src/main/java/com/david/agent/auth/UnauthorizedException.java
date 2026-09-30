package com.david.agent.auth;

/**
 * 未认证 / 认证失败。
 */
public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException(String message) {
        super(message);
    }
}
