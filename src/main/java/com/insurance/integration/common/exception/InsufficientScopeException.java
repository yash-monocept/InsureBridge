package com.insurance.integration.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when an authenticated partner lacks the scope required for an operation.
 * Maps to HTTP 403 Forbidden.
 */
@ResponseStatus(HttpStatus.FORBIDDEN)
public class InsufficientScopeException extends RuntimeException {

    public InsufficientScopeException(String requiredScope) {
        super("Insufficient permissions. Required scope: " + requiredScope);
    }
}
