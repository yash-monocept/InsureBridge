package com.insurance.integration.ingestion.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Response returned after a submission is ingested (Module 3).
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Ingestion response")
public class IngestionResponseDTO {

    @Schema(description = "Submission ID", example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID submissionId;

    @Schema(description = "External partner reference", example = "CAR-92831")
    private String externalReference;

    @Schema(description = "Idempotency key used", example = "idem_CAR-92831_20261116")
    private String idempotencyKey;

    @Schema(description = "Current submission status", example = "RECEIVED")
    private String status;

    @Schema(description = "Partner ID", example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID partnerId;

    @Schema(description = "Insurer code", example = "MAX_LIFE")
    private String insurerCode;

    @Schema(description = "Product code", example = "MOTOR_INSURANCE")
    private String productCode;

    @Schema(description = "Schema version used", example = "1.0")
    private String schemaVersion;

    @Schema(description = "Validation status (populated once validation runs)", example = "PENDING")
    private String validationStatus;

    @Schema(description = "Number of documents referenced", example = "2")
    private int documentCount;

    @Schema(description = "Timestamp the submission was received")
    private Instant receivedAt;

    @Schema(description = "Human-readable result message", example = "Submission received and queued for validation")
    private String message;

    @Schema(description = "Summary of document metadata (URLs excluded)", example = "[]")
    private List<DocumentMetadataDTO> documents;
}
