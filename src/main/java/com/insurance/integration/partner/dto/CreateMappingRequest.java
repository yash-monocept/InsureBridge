package com.insurance.integration.partner.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "Request to map a partner to an insurer")
public class CreateMappingRequest {

    @NotBlank(message = "Insurer code is required")
    @Schema(description = "Code of the insurer to map this partner to", example = "MAX_LIFE")
    private String insurerCode;

    @Schema(description = "JSON array of permitted product types for this mapping. " +
                           "Null means all product types are permitted.",
            example = "[\"MOTOR_INSURANCE\",\"TRAVEL_INSURANCE\"]")
    private String permittedProductTypes;
}
