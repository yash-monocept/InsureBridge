package com.insurance.integration.partner.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Partner-insurer mapping response")
public class MappingResponse {

    @Schema(description = "Mapping unique identifier")
    private UUID id;

    @Schema(description = "Partner code")
    private String partnerCode;

    @Schema(description = "Partner name")
    private String partnerName;

    @Schema(description = "Insurer code")
    private String insurerCode;

    @Schema(description = "Insurer name")
    private String insurerName;

    @Schema(description = "Whether the mapping is active")
    private boolean active;

    @Schema(description = "JSON array of permitted product types")
    private String permittedProductTypes;

    @Schema(description = "Admin who created this mapping")
    private String createdBy;

    @Schema(description = "Mapping creation timestamp")
    private Instant createdAt;
}
