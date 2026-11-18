package com.insurance.integration.schema.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

/**
 * Schema view returned by the schema administration APIs (Module 4).
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Product schema")
public class SchemaDTO {

    @Schema(description = "Schema ID", example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID id;

    @Schema(description = "Insurer ID (NULL = global schema)", example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID insurerId;

    @Schema(description = "Partner ID (NULL = global schema)", example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID partnerId;

    @Schema(description = "Product type", example = "MOTOR_INSURANCE")
    private String productType;

    @Schema(description = "Schema version", example = "1.0")
    private String schemaVersion;

    @Schema(description = "Schema name", example = "Global Motor Insurance Schema v1.0")
    private String schemaName;

    @Schema(description = "Whether the schema is active", example = "true")
    private boolean active;

    @Schema(description = "Timestamp the schema was created")
    private Instant createdAt;

    @Schema(description = "Timestamp the schema was last updated")
    private Instant updatedAt;
}
