package com.insurance.integration.partner.dto;

import com.insurance.integration.common.enums.PartnerType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "Request to register a new distribution partner")
public class CreatePartnerRequest {

    @NotBlank(message = "Partner code is required")
    @Pattern(regexp = "^[A-Z0-9_]{3,50}$",
             message = "Partner code must be 3-50 uppercase letters, digits or underscores")
    @Schema(description = "Unique business code for this partner", example = "AXIS_BANK")
    private String partnerCode;

    @NotBlank(message = "Partner name is required")
    @Size(min = 2, max = 200, message = "Partner name must be between 2 and 200 characters")
    @Schema(description = "Full legal name of the partner organisation", example = "Axis Bank Limited")
    private String partnerName;

    @NotNull(message = "Partner type is required")
    @Schema(description = "Type of distribution partner",
            example = "BANK",
            allowableValues = {"AUTOMOBILE_DEALER","AIRLINE","RAILWAY","BANK",
                               "FINANCIAL_INSTITUTION","TRAVEL_PLATFORM","ECOMMERCE","OTHER"})
    private PartnerType partnerType;

    @NotBlank(message = "Contact email is required")
    @Email(message = "Contact email must be a valid email address")
    @Schema(description = "Primary contact email for integration communications",
            example = "integrations@axisbank.com")
    private String contactEmail;

    @Size(max = 20, message = "Contact phone must not exceed 20 characters")
    @Schema(description = "Contact phone number", example = "+91-9876543210")
    private String contactPhone;

    @Size(max = 500, message = "Website URL must not exceed 500 characters")
    @Schema(description = "Partner website URL", example = "https://www.axisbank.com")
    private String websiteUrl;

    @Size(max = 2000, message = "Description must not exceed 2000 characters")
    @Schema(description = "Brief description of the partner and their integration use-case")
    private String description;

    @Schema(description = "JSON array of approved S3 host patterns for SSRF protection",
            example = "[\"*.s3.amazonaws.com\",\"axis-bank-docs.s3.ap-south-1.amazonaws.com\"]")
    private String approvedSourceHosts;

    @Min(value = 1, message = "Maximum document size must be at least 1 MB")
    @Max(value = 100, message = "Maximum document size must not exceed 100 MB")
    @Builder.Default
    @Schema(description = "Maximum allowed document size in MB for this partner", example = "50")
    private int maxDocumentSizeMb = 50;
}
