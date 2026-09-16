package com.cbo.players.exception;

import org.springframework.http.HttpStatus;

public class ResourceNotFoundException extends ApiException {

    public ResourceNotFoundException(String message, ErrorCode errorCode) {
        super(message, HttpStatus.NOT_FOUND, errorCode);
    }
}
