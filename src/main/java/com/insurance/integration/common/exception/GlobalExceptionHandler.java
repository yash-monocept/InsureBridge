package com.insurance.integration.common.exception;

import com.insurance.integration.common.response.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Centralised exception handler for all REST controllers.
 * Every exception that reaches the API layer must be handled here.
 *
 * IMPORTANT: Never log passwords, tokens, secrets, or full pre-signed URLs.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // ============================================================
    // 400 Bad Request
    // ============================================================

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {

        List<ApiError.FieldError> fieldErrors = ex.getBindingResult().getFieldErrors()
                .stream()
                .map(this::mapFieldError)
                .collect(Collectors.toList());

        log.warn("Validation failed at {}: {} field error(s)", request.getRequestURI(), fieldErrors.size());

        ApiError error = ApiError.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .errorType("VALIDATION_ERROR")
                .message("Request validation failed. Please check the field errors.")
                .path(request.getRequestURI())
                .fieldErrors(fieldErrors)
                .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ApiError> handleBusinessValidation(
            ValidationException ex, HttpServletRequest request) {

        log.warn("Business validation failed at {}: {}", request.getRequestURI(), ex.getMessage());

        ApiError error = ApiError.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .errorType("BUSINESS_VALIDATION_ERROR")
                .message(ex.getMessage())
                .path(request.getRequestURI())
                .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(SsrfAttemptException.class)
    public ResponseEntity<ApiError> handleSsrfAttempt(
            SsrfAttemptException ex, HttpServletRequest request) {

        // Log only minimal info — NEVER log the URL
        log.warn("SECURITY: SSRF attempt blocked at {} from IP {}",
                request.getRequestURI(), request.getRemoteAddr());

        ApiError error = ApiError.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .errorType("INVALID_URL")
                .message("Provided URL is not allowed.")
                .path(request.getRequestURI())
                .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiError> handleMissingParam(
            MissingServletRequestParameterException ex, HttpServletRequest request) {

        ApiError error = ApiError.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .errorType("MISSING_PARAMETER")
                .message("Required parameter '" + ex.getParameterName() + "' is missing.")
                .path(request.getRequestURI())
                .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {

        ApiError error = ApiError.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .errorType("INVALID_PARAMETER_TYPE")
                .message("Invalid value for parameter '" + ex.getName() + "'.")
                .path(request.getRequestURI())
                .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    // ============================================================
    // 401 Unauthorized
    // ============================================================

    @ExceptionHandler(PartnerAuthenticationException.class)
    public ResponseEntity<ApiError> handlePartnerAuth(
            PartnerAuthenticationException ex, HttpServletRequest request) {

        log.warn("Partner authentication failed at {}", request.getRequestURI());

        ApiError error = ApiError.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.UNAUTHORIZED.value())
                .errorType("AUTHENTICATION_FAILED")
                .message(ex.getMessage())
                .path(request.getRequestURI())
                .build();

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiError> handleSpringAuth(
            AuthenticationException ex, HttpServletRequest request) {

        log.warn("Authentication failed at {}: {}", request.getRequestURI(), ex.getMessage());

        ApiError error = ApiError.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.UNAUTHORIZED.value())
                .errorType("UNAUTHORIZED")
                .message("Authentication required.")
                .path(request.getRequestURI())
                .build();

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
    }

    // ============================================================
    // 403 Forbidden
    // ============================================================

    @ExceptionHandler(PartnerNotActiveException.class)
    public ResponseEntity<ApiError> handlePartnerNotActive(
            PartnerNotActiveException ex, HttpServletRequest request) {

        log.warn("Inactive partner access attempt at {}", request.getRequestURI());

        ApiError error = ApiError.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.FORBIDDEN.value())
                .errorType("PARTNER_NOT_ACTIVE")
                .message(ex.getMessage())
                .path(request.getRequestURI())
                .build();

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(
            AccessDeniedException ex, HttpServletRequest request) {

        log.warn("Access denied at {} for {}",
                request.getRequestURI(), request.getRemoteUser());

        ApiError error = ApiError.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.FORBIDDEN.value())
                .errorType("FORBIDDEN")
                .message("You do not have permission to perform this action.")
                .path(request.getRequestURI())
                .build();

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
    }

    // ============================================================
    // 404 Not Found
    // ============================================================

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(
            ResourceNotFoundException ex, HttpServletRequest request) {

        log.warn("Resource not found at {}: {}", request.getRequestURI(), ex.getMessage());

        ApiError error = ApiError.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.NOT_FOUND.value())
                .errorType("NOT_FOUND")
                .message(ex.getMessage())
                .path(request.getRequestURI())
                .build();

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    // ============================================================
    // 409 Conflict
    // ============================================================

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ApiError> handleDuplicate(
            DuplicateResourceException ex, HttpServletRequest request) {

        log.warn("Duplicate resource at {}: {}", request.getRequestURI(), ex.getMessage());

        ApiError error = ApiError.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.CONFLICT.value())
                .errorType("DUPLICATE_RESOURCE")
                .message(ex.getMessage())
                .path(request.getRequestURI())
                .build();

        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    // ============================================================
    // 500 Internal Server Error
    // ============================================================

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneral(
            Exception ex, HttpServletRequest request) {

        // Log with full stack trace — but never expose to client
        log.error("Unexpected error at {}: {}", request.getRequestURI(), ex.getMessage(), ex);

        ApiError error = ApiError.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .errorType("INTERNAL_SERVER_ERROR")
                .message("An unexpected error occurred. Please contact support if the problem persists.")
                .path(request.getRequestURI())
                .build();

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }

    // ============================================================
    // Helpers
    // ============================================================

    private ApiError.FieldError mapFieldError(FieldError fe) {
        return ApiError.FieldError.builder()
                .field(fe.getField())
                .rejectedValue(fe.getRejectedValue() != null ? fe.getRejectedValue().toString() : null)
                .message(fe.getDefaultMessage())
                .build();
    }
}
