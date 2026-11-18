package com.insurance.integration.schema.entity;

import com.insurance.integration.common.entity.BaseEntity;
import jakarta.persistence.Access;
import jakarta.persistence.AccessType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

/**
 * Required-document rule within a product schema (Module 4 - Schema Validation).
 *
 * <p>Maps to the {@code document_rules} table created by Flyway migration
 * {@code V3__Create_schema_engine.sql}. Declares which document types a submission must
 * include and the constraints applied to each.</p>
 */
@Entity
@Access(AccessType.FIELD)
@Table(name = "document_rules")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentRule extends BaseEntity {

    /** Schema this rule belongs to. */
    @Column(name = "schema_id", nullable = false)
    @JdbcTypeCode(SqlTypes.UUID)
    private UUID schemaId;

    /** Document type (e.g. IDENTITY_PROOF, VEHICLE_DOCUMENT, BOOKING_CONFIRMATION). */
    @Column(name = "document_type", length = 100, nullable = false)
    private String documentType;

    /** Whether this document type is mandatory. */
    @Column(name = "is_required", nullable = false)
    @Builder.Default
    private boolean required = true;

    /** JSON array of accepted MIME types, e.g. ["application/pdf","image/jpeg"]. */
    @Column(name = "allowed_mime_types", columnDefinition = "TEXT")
    private String allowedMimeTypes;

    /** Maximum permitted file size in megabytes. */
    @Column(name = "max_size_mb", nullable = false)
    @Builder.Default
    private int maxSizeMb = 10;

    /** Human-readable description of the document requirement. */
    @Column(name = "description", length = 500)
    private String description;
}
