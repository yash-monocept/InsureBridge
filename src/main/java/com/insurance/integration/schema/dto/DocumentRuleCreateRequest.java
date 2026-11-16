package com.insurance.integration.schema.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Data;

/**
 * Request to create a document requirement rule (Module 4 - admin).
 */
@Data
@Builder
@Schema(description = "Document rule creation request")
public class DocumentRuleCreateRequest {

    @NotBlank(message = "Document type is required")
    @Size(max = 100, message = "Document type must not exceed 100 characters")
    @Schema(description = "Document type", example = "VEHICLE_DOCUMENT",
            allowableValues = {"IDENTITY_PROOF", "VEHICLE_DOCUMENT", "BOOKING_CONFIRMATION",
                "MEDICAL_REPORT", "POLICY_DOCUMENT", "OTHER"})
    private String documentType;

    @Schema(description = "Whether the document is mandatory", example = true)
    @NotNull(message = "Required flag is required")
    private Boolean required;

    @Schema(description = "JSON array of accepted MIME types", example = "[\"application/pdf\",\"image/png\"]")
    private String allowedMimeTypes;

    @Schema(description = "Maximum file size in MB", example = 10)
    @NotNull(message = "Max size MB is required")
    private Integer maxSizeMb;

    @Size(max = 500, message = "Description must not exceed 500 characters")
    @Schema(description = "Human-readable description", example = "Vehicle registration certificate")
    private String description;
}
