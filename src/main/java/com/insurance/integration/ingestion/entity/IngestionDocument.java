package com.insurance.integration.ingestion.entity;

import com.insurance.integration.common.entity.BaseEntity;
import com.insurance.integration.common.enums.DocumentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Access;
import jakarta.persistence.AccessType;
import jakarta.persistence.FetchType;

import java.util.UUID;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Individual document reference within an ingestion request (Module 3 - Request Ingestion).
 *
 * <p>Maps to the {@code ingestion_documents} table created by Flyway migration
 * {@code V4__Create_ingestion.sql}. The pre-signed S3 URL supplied by the partner is stored
 * here (never exposed in API responses) alongside a SHA-256 hash used for deduplication
 * without revealing the full URL.</p>
 */
@Entity
@Access(AccessType.FIELD)
@Table(name = "ingestion_documents")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IngestionDocument extends BaseEntity {

    /** The ingestion request this document belongs to. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ingestion_request_id", nullable = false, insertable = false, updatable = false)
    private IngestionRequest ingestionRequest;

    @Column(name = "ingestion_request_id", nullable = false)
    @JdbcTypeCode(SqlTypes.UUID)
    private UUID ingestionRequestId;

    /** Document type (e.g. IDENTITY_PROOF, VEHICLE_DOCUMENT, BOOKING_CONFIRMATION). */
    @Column(name = "document_type", length = 100, nullable = false)
    private String documentType;

    /**
     * Partner-supplied pre-signed S3 URL.
     * <p>SECURITY: Never returned in API responses and never written to logs in full.</p>
     */
    @Column(name = "source_s3_url", length = 2000, nullable = false)
    private String sourceS3Url;

    /** SHA-256 hash of the pre-signed URL for deduplication without exposing the full URL. */
    @Column(name = "source_url_hash", length = 64)
    private String sourceUrlHash;

    /** S3 bucket name parsed from the source URL (audit only). */
    @Column(name = "source_bucket", length = 200)
    private String sourceBucket;

    /** S3 object key parsed from the source URL (audit only). */
    @Column(name = "source_key", length = 1000)
    private String sourceKey;

    /** Original file name as declared by the partner. */
    @Column(name = "original_filename", length = 500)
    private String originalFilename;

    /** Declared content type (e.g. application/pdf). */
    @Column(name = "declared_mime_type", length = 100)
    private String declaredMimeType;

    /** Declared file size in bytes. */
    @Column(name = "declared_size_bytes")
    private Long declaredSizeBytes;

    /** Current status of this document within the pipeline. */
    @Column(name = "document_status", length = 50, nullable = false)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private DocumentStatus documentStatus = DocumentStatus.PENDING;
}
