package com.insurance.integration.ingestion.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * Customer information for display in submission details.
 */
@Data
@Builder
@Schema(description = "Customer detail")
public class CustomerDetailDTO {

    @Schema(description = "Customer full name", example = "Rahul Sharma")
    private String name;

    @Schema(description = "Customer mobile number", example = "9999999999")
    private String mobile;

    @Schema(description = "Customer email address", example = "rahul@example.com")
    private String email;
}
