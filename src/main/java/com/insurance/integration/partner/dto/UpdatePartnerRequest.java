package com.insurance.integration.partner.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "Request to update an existing partner's details")
public class UpdatePartnerRequest {

    @Size(min = 2, max = 200, message = "Partner name must be between 2 and 200 characters")
    @Schema(description = "Updated full name of the partner organisation")
    private String partnerName;

    @Email(message = "Contact email must be a valid email address")
    @Schema(description = "Updated contact email")
    private String contactEmail;

    @Size(max = 20)
    @Schema(description = "Updated contact phone")
    private String contactPhone;

    @Size(max = 500)
    @Schema(description = "Updated website URL")
    private String websiteUrl;

    @Size(max = 2000)
    @Schema(description = "Updated description")
    private String description;

    @Schema(description = "Updated JSON array of approved S3 host patterns")
    private String approvedSourceHosts;

    @Min(1) @Max(100)
    @Schema(description = "Updated maximum document size in MB")
    private Integer maxDocumentSizeMb;
}
