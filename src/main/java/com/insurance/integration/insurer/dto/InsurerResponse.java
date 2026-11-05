package com.insurance.integration.insurer.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.insurance.integration.common.enums.InsurerType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

/**
 * Insurer response DTO.
 * SECURITY: IAM role ARN, webhook_secret, and bucket details are EXCLUDED from this DTO.
 * Those fields are server-side configuration only and must never be sent to clients.
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Insurance provider response")
public class InsurerResponse {

    @Schema(description = "Insurer unique identifier")
    private UUID id;

    @Schema(description = "Short unique insurer code", example = "MAX_LIFE")
    private String insurerCode;

    @Schema(description = "Full insurer name", example = "Max Life Insurance Company Limited")
    private String insurerName;

    @Schema(description = "Type of insurance", example = "LIFE")
    private InsurerType insurerType;

    @Schema(description = "Contact email")
    private String contactEmail;

    @Schema(description = "Whether the insurer is active")
    private boolean active;

    // NOTE: destinationBucketName, destinationIamRoleArn, webhookSecret intentionally omitted

    @Schema(description = "Whether destination S3 bucket is configured")
    private boolean destinationConfigured;

    @Schema(description = "Whether SNS notification is configured")
    private boolean snsConfigured;

    @Schema(description = "Whether webhook notification is configured")
    private boolean webhookConfigured;

    @Schema(description = "Record creation timestamp")
    private Instant createdAt;

    @Schema(description = "Record last update timestamp")
    private Instant updatedAt;
}
