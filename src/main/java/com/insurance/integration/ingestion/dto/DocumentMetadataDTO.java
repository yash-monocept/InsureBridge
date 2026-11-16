package com.insurance.integration.ingestion.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

/**
 * Sanitised document metadata exposed to API consumers.
 *
 * <p>The full pre-signed S3 URL is deliberately excluded — only metadata safe for display
 * is included.</p>
 */
@Data
@Builder
@Schema(description = "Sanitised document metadata")
public class DocumentMetadataDTO {

    @Schema(description = "Document ID", example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID documentId;

    @Schema(description = "Document type", example = "IDENTITY_PROOF")
    private String documentType;

    @Schema(description = "Original file name", example = "identity.pdf")
    private String fileName;

    @Schema(description = "Declared MIME type", example = "application/pdf")
    private String declaredMimeType;

    @Schema(description = "Declared size in bytes", example = 245760)
    private Long declaredSizeBytes;

    @Schema(description = "Document processing status", example = "PENDING")
    private String documentStatus;

    @Schema(description = "Timestamp the document reference was recorded")
    private Instant createdAt;
}
