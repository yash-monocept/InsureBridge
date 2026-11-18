package com.insurance.integration.ingestion.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

/**
 * Summary of a submission used in list views (Module 3).
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Submission summary for listing")
public class SubmissionSummaryDTO {

    @Schema(description = "Submission ID", example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID submissionId;

    @Schema(description = "External partner reference", example = "CAR-92831")
    private String externalReference;

    @Schema(description = "Partner name", example = "Axis Bank Limited")
    private String partnerName;

    @Schema(description = "Insurer code", example = "MAX_LIFE")
    private String insurerCode;

    @Schema(description = "Product code", example = "MOTOR_INSURANCE")
    private String productCode;

    @Schema(description = "Submission timestamp")
    private Instant receivedAt;

    @Schema(description = "Validation status", example = "VALIDATED")
    private String validationStatus;

    @Schema(description = "Processing status", example = "RECEIVED")
    private String processingStatus;

    @Schema(description = "Number of failed validation checks", example = "0")
    private int validationErrorCount;

    @Schema(description = "Number of documents referenced", example = "2")
    private int documentCount;
}
