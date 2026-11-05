package com.insurance.integration.partner.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.insurance.integration.common.enums.PartnerType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

/**
 * Partner response DTO.
 * SECURITY: approved_source_hosts and internal config are included for admin only.
 * A partner-facing response should use PartnerSummaryResponse instead.
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Partner details response (admin view)")
public class PartnerResponse {

    @Schema(description = "Partner unique identifier")
    private UUID id;

    @Schema(description = "Short unique partner code", example = "AXIS_BANK")
    private String partnerCode;

    @Schema(description = "Full partner name", example = "Axis Bank Limited")
    private String partnerName;

    @Schema(description = "Partner type", example = "BANK")
    private PartnerType partnerType;

    @Schema(description = "Contact email")
    private String contactEmail;

    @Schema(description = "Contact phone")
    private String contactPhone;

    @Schema(description = "Website URL")
    private String websiteUrl;

    @Schema(description = "Description")
    private String description;

    @Schema(description = "Whether the partner is active")
    private boolean active;

    @Schema(description = "Approved S3 host patterns (JSON array)")
    private String approvedSourceHosts;

    @Schema(description = "Maximum document size in MB")
    private int maxDocumentSizeMb;

    @Schema(description = "Admin who created this record")
    private String createdBy;

    @Schema(description = "Record creation timestamp")
    private Instant createdAt;

    @Schema(description = "Record last update timestamp")
    private Instant updatedAt;
}
