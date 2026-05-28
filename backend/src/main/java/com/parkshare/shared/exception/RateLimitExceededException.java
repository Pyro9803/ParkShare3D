package com.parkshare.shared.exception;

import org.springframework.http.HttpStatus;

public class RateLimitExceededException extends BusinessException {

    public RateLimitExceededException(String message) {
        super("RATE_LIMIT_EXCEEDED", message, HttpStatus.TOO_MANY_REQUESTS);
    }
}
