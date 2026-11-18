package com.insurance.integration.schema.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Structured validation result produced by Module 4's schema engine.
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Schema validation result")
public class ValidationResultDTO {

    @Schema(description = "Submission ID", example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID submissionId;

    @Schema(description = "Schema version validated against", example = "1.0")
    private String schemaVersion;

    @Schema(description = "Overall validation status", example = "VALIDATION_FAILED",
            allowableValues = {"VALIDATED", "VALIDATION_FAILED"})
    private String validationStatus;

    @Schema(description = "Total number of checks performed", example = "8")
    private int totalChecks;

    @Schema(description = "Number of checks that passed", example = "6")
    private int passedChecks;

    @Schema(description = "Number of checks that failed", example = "2")
    private int failedChecks;

    @Schema(description = "Field-level validation errors")
    private List<ValidationErrorDTO> errors;

    @Schema(description = "Timestamp validation was performed")
    private Instant validatedAt;
}
