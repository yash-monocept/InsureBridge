package com.insurance.integration.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;

/**
 * Standard error response structure for all API errors.
 * Every error response must follow this format.
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiError {

    /** ISO-8601 timestamp of when the error occurred. */
    private final Instant timestamp;

    /** HTTP status code (e.g. 400, 401, 404). */
    private final int status;

    /** Error category (e.g. VALIDATION_ERROR, NOT_FOUND, UNAUTHORIZED). */
    private final String errorType;

    /** Human-readable error message. */
    private final String message;

    /** Request path where the error occurred. */
    private final String path;

    /** For validation errors: field-level details. */
    private final List<FieldError> fieldErrors;

    /**
     * Individual field-level validation error detail.
     */
    @Getter
    @Builder
    public static class FieldError {
        private final String field;
        private final String rejectedValue;
        private final String message;
    }
}
