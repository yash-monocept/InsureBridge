package com.insurance.integration.ingestion.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.insurance.integration.schema.dto.ValidationResultDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Detailed view of a single submission (Module 3 - Submission Details).
 *
 * <p>Organises information into clear sections: overview, partner, insurer, customer,
 * product, document metadata, validation results and processing history.</p>
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Submission detail view")
public class SubmissionDetailDTO {

    @Schema(description = "Submission ID", example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID submissionId;

    @Schema(description = "External partner reference", example = "CAR-92831")
    private String externalReference;

    @Schema(description = "Idempotency key used", example = "idem_CAR-92831_20261116")
    private String idempotencyKey;

    @Schema(description = "Current processing status", example = "RECEIVED")
    private String status;

    @Schema(description = "Validation status", example = "VALIDATED")
    private String validationStatus;

    @Schema(description = "Partner ID", example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID partnerId;

    @Schema(description = "Partner code", example = "AXIS_BANK")
    private String partnerCode;

    @Schema(description = "Partner name", example = "Axis Bank Limited")
    private String partnerName;

    @Schema(description = "Insurer ID", example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID insurerId;

    @Schema(description = "Insurer code", example = "MAX_LIFE")
    private String insurerCode;

    @Schema(description = "Insurer name", example = "Max Life Insurance Company Limited")
    private String insurerName;

    @Schema(description = "Product code", example = "MOTOR_INSURANCE")
    private String productCode;

    @Schema(description = "Schema version used", example = "1.0")
    private String schemaVersion;

    @Schema(description = "Customer information")
    private CustomerDetailDTO customer;

    @Schema(description = "Product-specific application data (flexible JSON)")
    private JsonNode applicationData;

    @Schema(description = "Document metadata references")
    private List<DocumentMetadataDTO> documents;

    @Schema(description = "Validation result", example = "{}")
    private ValidationResultDTO validationResult;

    @Schema(description = "Timestamp the submission was received")
    private Instant receivedAt;

    @Schema(description = "Timestamp validation completed")
    private Instant validatedAt;

    @Schema(description = "Sanitised processing history summary", example = "Received -> Validating -> Validated")
    private String processingHistory;
}
