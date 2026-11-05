package com.insurance.integration.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when a submitted URL is detected as a potential SSRF attempt.
 * The offending URL must NEVER be logged in full.
 * Maps to HTTP 400 Bad Request.
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class SsrfAttemptException extends RuntimeException {

    public SsrfAttemptException(String message) {
        super(message);
    }
}
