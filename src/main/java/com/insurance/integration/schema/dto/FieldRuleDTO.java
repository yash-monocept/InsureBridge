package com.insurance.integration.schema.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.insurance.integration.common.enums.FieldDataType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Field rule view returned by the schema administration APIs (Module 4).
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Field validation rule")
public class FieldRuleDTO {

    @Schema(description = "Rule ID", example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID id;

    @Schema(description = "Schema ID", example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID schemaId;

    @Schema(description = "Dot-notation field path", example = "customer.mobile")
    private String fieldPath;

    @Schema(description = "Human-readable label", example = "Customer Mobile Number")
    private String displayName;

    @Schema(description = "Whether the field is mandatory", example = "true")
    private boolean required;

    @Schema(description = "Expected data type", example = "STRING",
            allowableValues = {"STRING", "NUMBER", "DATE", "BOOLEAN", "ENUM"})
    private FieldDataType dataType;

    @Schema(description = "Minimum string length", example = "1")
    private Integer minLength;

    @Schema(description = "Maximum string length", example = "15")
    private Integer maxLength;

    @Schema(description = "Minimum numeric value", example = "18")
    private BigDecimal minValue;

    @Schema(description = "Maximum numeric value", example = "100")
    private BigDecimal maxValue;

    @Schema(description = "Regular expression the value must match", example = "^[0-9]{10}$")
    private String regexPattern;

    @Schema(description = "JSON array of permitted values for ENUM fields", example = "[\"LIFE\",\"HEALTH\"]")
    private String allowedValues;

    @Schema(description = "Expected date format", example = "yyyy-MM-dd")
    private String dateFormat;

    @Schema(description = "Custom error message", example = "Mobile number is required")
    private String errorMessage;
}
