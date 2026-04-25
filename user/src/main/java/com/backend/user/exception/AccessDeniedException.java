package com.backend.user.exception;

import lombok.extern.slf4j.Slf4j;

/**
 * 权限拒绝异常
 * 用于权限验证失败时的异常处理，不打印堆栈跟踪
 */
@Slf4j
public class AccessDeniedException extends RuntimeException {

    public AccessDeniedException(String message) {
        super(message, null, false, false);
        log.warn("权限验证失败: {}", message);
    }

    public AccessDeniedException(String message, Object... args) {
        super(String.format(message, args), null, false, false);
        log.warn("权限验证失败: {}", String.format(message, args));
    }
}
