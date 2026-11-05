package com.insurance.integration.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.List;
import java.util.Map;

/**
 * Thrown when business or schema validation fails.
 * Maps to HTTP 400 Bad Request.
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class ValidationException extends RuntimeException {

    private final List<Map<String, String>> validationErrors;

    public ValidationException(String message) {
        super(message);
        this.validationErrors = List.of();
    }

    public ValidationException(String message, List<Map<String, String>> validationErrors) {
        super(message);
        this.validationErrors = validationErrors;
    }

    public List<Map<String, String>> getValidationErrors() {
        return validationErrors;
    }
}
