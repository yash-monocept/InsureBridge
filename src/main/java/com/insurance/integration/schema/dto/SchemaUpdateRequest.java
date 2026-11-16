package com.insurance.integration.schema.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Data;

/**
 * Request to update an existing product schema (Module 4 - admin).
 *
 * <p>Historical schema versions are never modified in place; a new version should be
 * created instead. This endpoint only updates the name/active flag of an existing version.</p>
 */
@Data
@Builder
@Schema(description = "Schema update request")
public class SchemaUpdateRequest {

    @NotBlank(message = "Schema name is required")
    @Size(max = 200, message = "Schema name must not exceed 200 characters")
    @Schema(description = "Schema name", example = "Motor Insurance Schema v1.0")
    private String schemaName;

    @Schema(description = "Whether the schema is active", example = true)
    private Boolean active;
}
