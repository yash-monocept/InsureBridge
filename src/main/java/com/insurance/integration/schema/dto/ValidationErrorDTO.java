package com.insurance.integration.schema.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * Individual field-level validation error (Module 4).
 */
@Data
@Builder
@Schema(description = "Field-level validation error")
public class ValidationErrorDTO {

    @Schema(description = "Dot-notation field path that failed", example = "customer.mobile")
    private String field;

    @Schema(description = "Machine-readable error code", example = "INVALID_FORMAT")
    private String code;

    @Schema(description = "Human-readable error message", example = "Mobile number is invalid")
    private String message;

    @Schema(description = "Value that was rejected (may be redacted for sensitive fields)")
    private Object rejectedValue;
}
