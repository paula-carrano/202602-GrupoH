package com.cbo.players.exception;

import org.springframework.http.HttpStatus;

public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final ErrorCode errorCode;

    public ApiException(String message, HttpStatus status, ErrorCode errorCode) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }

    public ApiException(String message, Throwable cause, HttpStatus status, ErrorCode errorCode) {
        super(message, cause);
        this.status = status;
        this.errorCode = errorCode;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
