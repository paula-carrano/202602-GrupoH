package com.cbo.players.exception;

import org.springframework.http.HttpStatus;

public class InvalidCredentialsException extends ApiException {

    public InvalidCredentialsException(String message) {
        super(message, HttpStatus.UNAUTHORIZED, ErrorCode.INVALID_CREDENTIALS);
    }
}
