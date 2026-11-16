package com.insurance.integration.schema.dto;

import com.insurance.integration.common.enums.FieldDataType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Request to create a field validation rule (Module 4 - admin).
 */
@Data
@Builder
@Schema(description = "Field rule creation request")
public class FieldRuleCreateRequest {

    @NotBlank(message = "Field path is required")
    @Size(max = 200, message = "Field path must not exceed 200 characters")
    @Schema(description = "Dot-notation field path", example = "vehicle.registrationNumber")
    private String fieldPath;

    @NotBlank(message = "Display name is required")
    @Size(max = 200, message = "Display name must not exceed 200 characters")
    @Schema(description = "Human-readable label", example = "Vehicle Registration Number")
    private String displayName;

    @Schema(description = "Whether the field is mandatory", example = true)
    @NotNull(message = "Required flag is required")
    private Boolean required;

    @NotNull(message = "Data type is required")
    @Schema(description = "Expected data type", example = "STRING",
            allowableValues = {"STRING", "NUMBER", "DATE", "BOOLEAN", "ENUM"})
    private FieldDataType dataType;

    @Schema(description = "Minimum string length", example = 1)
    private Integer minLength;

    @Schema(description = "Maximum string length", example = 20)
    private Integer maxLength;

    @Schema(description = "Minimum numeric value", example = 1900)
    private BigDecimal minValue;

    @Schema(description = "Maximum numeric value", example = 2100)
    private BigDecimal maxValue;

    @Schema(description = "Regular expression the value must match", example = "^[A-Z]{2}[0-9]{2}$")
    private String regexPattern;

    @Schema(description = "JSON array of permitted values for ENUM fields", example = "[\"ACTIVE\",\"EXPIRED\"]")
    private String allowedValues;

    @Schema(description = "Expected date format", example = "yyyy-MM-dd")
    private String dateFormat;

    @Schema(description = "Custom error message", example = "Registration number is required")
    private String errorMessage;
}
