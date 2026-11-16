package com.insurance.integration.ingestion.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Data;

/**
 * A single supporting document reference within an ingestion request (Module 3).
 *
 * <p>Only document metadata is captured here. The actual S3 object is not downloaded or
 * transferred at this stage — that belongs to Modules 6-9.</p>
 */
@Data
@Builder
@Schema(description = "Supporting document reference")
public class DocumentSubmissionDTO {

    @NotBlank(message = "Document type is required")
    @Size(max = 100, message = "Document type must not exceed 100 characters")
    @Schema(description = "Type of document",
            example = "IDENTITY_PROOF",
            allowableValues = {"IDENTITY_PROOF", "VEHICLE_DOCUMENT", "BOOKING_CONFIRMATION",
                "MEDICAL_REPORT", "POLICY_DOCUMENT", "OTHER"})
    private String documentType;

    @NotBlank(message = "File name is required")
    @Size(max = 500, message = "File name must not exceed 500 characters")
    @Schema(description = "Original file name", example = "identity.pdf")
    private String fileName;

    @NotBlank(message = "Content type is required")
    @Size(max = 100, message = "Content type must not exceed 100 characters")
    @Schema(description = "Declared MIME type", example = "application/pdf")
    private String contentType;

    @NotBlank(message = "Source URL is required")
    @Size(max = 2000, message = "Source URL must not exceed 2000 characters")
    @Schema(description = "Partner pre-signed S3 URL for the document (HTTPS only)",
            example = "https://bucket.s3.ap-south-1.amazonaws.com/doc.pdf?signature=...")
    private String sourceUrl;
}
