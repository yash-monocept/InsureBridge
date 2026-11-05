package com.insurance.integration.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when partner authentication fails.
 * Maps to HTTP 401 Unauthorized.
 */
@ResponseStatus(HttpStatus.UNAUTHORIZED)
public class PartnerAuthenticationException extends RuntimeException {

    public PartnerAuthenticationException(String message) {
        super(message);
    }
}
