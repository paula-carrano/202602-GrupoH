package com.cbo.players.exception;

import org.springframework.http.HttpStatus;

public class UserAlreadyExistsException extends ApiException {

    public UserAlreadyExistsException(String message, ErrorCode errorCode) {
        super(message, HttpStatus.CONFLICT, errorCode);
    }
}
