package com.insurance.integration.ingestion.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Canonical request envelope for submitting an insurance application (Module 3).
 *
 * <p>The {@code applicationData} field is intentionally flexible (a JSON tree) so the same
 * envelope can serve motor, travel, health and other insurance categories. The product-specific
 * section is validated against Module 4's configurable field rules.</p>
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Insurance application submission request envelope")
public class IngestionRequestDTO {

    @NotBlank(message = "Partner reference ID is required")
    @Size(max = 200, message = "Partner reference ID must not exceed 200 characters")
    @Schema(description = "External partner reference for this transaction",
            example = "CAR-92831")
    private String partnerReferenceId;

    @NotBlank(message = "Insurer code is required")
    @Size(max = 50, message = "Insurer code must not exceed 50 characters")
    @Schema(description = "Code of the insurer the submission is addressed to",
            example = "MAX_LIFE")
    private String insurerCode;

    @NotBlank(message = "Product code is required")
    @Size(max = 100, message = "Product code must not exceed 100 characters")
    @Schema(description = "Insurance product code",
            example = "MOTOR_INSURANCE",
            allowableValues = {"MOTOR_INSURANCE", "TRAVEL_INSURANCE", "HEALTH_INSURANCE",
                "LIFE_INSURANCE", "HOME_INSURANCE", "COMMERCIAL_INSURANCE"})
    private String productCode;

    @Size(max = 20, message = "Schema version must not exceed 20 characters")
    @Schema(description = "Schema version to validate against (defaults to active version)",
            example = "1.0")
    private String schemaVersion;

    @Valid
    @NotNull(message = "Customer information is required")
    @Schema(description = "Customer information submitted by the partner")
    private CustomerDTO customer;

    @Valid
    @NotNull(message = "Application data is required")
    @Schema(description = "Product-specific application data (flexible JSON structure)",
            example = """
                    {
                      "vehicle": {
                        "make": "Hyundai",
                        "model": "Creta",
                        "registrationNumber": "DL01AB1234",
                        "manufacturingYear": 2026
                      }
                    }""")
    private JsonNode applicationData;

    @Valid
    @NotEmpty(message = "At least one document reference is required")
    @Size(max = 20, message = "A submission may reference at most 20 documents")
    @Schema(description = "Supporting document references submitted with the application")
    private List<DocumentSubmissionDTO> documents;

    @Size(max = 200, message = "Idempotency key must not exceed 200 characters")
    @Schema(description = "Idempotency key to prevent duplicate submissions",
            example = "idem_CAR-92831_20261116")
    private String idempotencyKey;
}
