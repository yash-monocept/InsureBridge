package com.insurance.integration.schema.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

/**
 * Request to create a new product schema (Module 4 - admin).
 */
@Data
@Builder
@Schema(description = "Schema creation request")
public class SchemaCreateRequest {

    @Schema(description = "Insurer ID (omit for a global schema)",
            example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID insurerId;

    @Schema(description = "Partner ID (omit for a global schema)",
            example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID partnerId;

    @NotBlank(message = "Product type is required")
    @Size(max = 100, message = "Product type must not exceed 100 characters")
    @Schema(description = "Insurance product type",
            example = "MOTOR_INSURANCE",
            allowableValues = {"MOTOR_INSURANCE", "TRAVEL_INSURANCE", "HEALTH_INSURANCE",
                "LIFE_INSURANCE", "HOME_INSURANCE", "COMMERCIAL_INSURANCE"})
    private String productType;

    @NotBlank(message = "Schema version is required")
    @Size(max = 20, message = "Schema version must not exceed 20 characters")
    @Schema(description = "Schema version", example = "1.0")
    private String schemaVersion;

    @NotBlank(message = "Schema name is required")
    @Size(max = 200, message = "Schema name must not exceed 200 characters")
    @Schema(description = "Schema name", example = "Motor Insurance Schema v1.0")
    private String schemaName;

    @Schema(description = "Whether the schema is active", example = true)
    @NotNull(message = "Active flag is required")
    private Boolean active;
}
