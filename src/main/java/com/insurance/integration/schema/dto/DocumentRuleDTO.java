package com.insurance.integration.schema.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

/**
 * Document rule view returned by the schema administration APIs (Module 4).
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Document requirement rule")
public class DocumentRuleDTO {

    @Schema(description = "Rule ID", example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID id;

    @Schema(description = "Schema ID", example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID schemaId;

    @Schema(description = "Document type", example = "IDENTITY_PROOF")
    private String documentType;

    @Schema(description = "Whether the document is mandatory", example = "true")
    private boolean required;

    @Schema(description = "JSON array of accepted MIME types", example = "[\"application/pdf\",\"image/jpeg\"]")
    private String allowedMimeTypes;

    @Schema(description = "Maximum file size in MB", example = "10")
    private int maxSizeMb;

    @Schema(description = "Human-readable description", example = "Government-issued identity proof")
    private String description;
}
