package com.backend.utils.exception;

import lombok.Getter;

/**
 * Business exception with HTTP status.
 */
@Getter
public class BizException extends RuntimeException {

    private final int httpStatus;

    public BizException(String message) {
        super(message);
        this.httpStatus = 400;
    }

    public BizException(int httpStatus, String message) {
        super(message);
        this.httpStatus = httpStatus;
    }

    public BizException(int httpStatus, String message, Throwable cause) {
        super(message, cause);
        this.httpStatus = httpStatus;
    }
}