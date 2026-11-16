package com.insurance.integration.ingestion.entity;

import com.insurance.integration.common.entity.BaseEntity;
import com.insurance.integration.common.enums.IngestionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Master record for each partner insurance submission (Module 3 - Request Ingestion).
 *
 * <p>Maps to the {@code ingestion_requests} table created by Flyway migration
 * {@code V4__Create_ingestion.sql}. One row is persisted per partner API submission call
 * and drives the entire request-level processing lifecycle.</p>
 *
 * <p>SECURITY: The full pre-signed source URL is never stored here — it lives on
 * {@link IngestionDocument} and is never returned in API responses or written to logs.</p>
 */
@Entity
@Table(name = "ingestion_requests")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IngestionRequest extends BaseEntity {

    /** External partner reference for this transaction (e.g. CAR-92831). */
    @Column(name = "external_reference", length = 200)
    private String externalReference;

    /** Partner-supplied idempotency key used to prevent duplicate submissions. */
    @Column(name = "idempotency_key", length = 200, nullable = false, unique = true)
    private String idempotencyKey;

    /** Authenticated partner that submitted the request. */
    @Column(name = "partner_id", nullable = false)
    private UUID partnerId;

    /** Insurer the submission is addressed to. */
    @Column(name = "insurer_id", nullable = false)
    private UUID insurerId;

    /** Insurance product type (e.g. MOTOR_INSURANCE, TRAVEL_INSURANCE). */
    @Column(name = "product_type", length = 100, nullable = false)
    private String productType;

    /** Schema version used to validate this submission (nullable until validation runs). */
    @Column(name = "schema_id")
    private UUID schemaId;

    /** Current request-level processing status. */
    @Column(name = "status", length = 50, nullable = false)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private IngestionStatus status = IngestionStatus.RECEIVED;

    /** Customer information submitted by the partner (serialised JSON). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "customer_data", columnDefinition = "JSONB", nullable = false)
    @Builder.Default
    private String customerData = "{}";

    /** Product-specific application data (serialised JSON, product-structure agnostic). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "product_data", columnDefinition = "JSONB", nullable = false)
    @Builder.Default
    private String productData = "{}";

    /** Arbitrary submission metadata (serialised JSON). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "JSONB")
    private String metadata;

    /** Structured validation output produced by Module 4 (serialised JSON). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "validation_result", columnDefinition = "JSONB")
    private String validationResult;

    /** Number of failed validation checks, denormalised for fast filtering. */
    @Column(name = "validation_error_count", nullable = false)
    @Builder.Default
    private int validationErrorCount = 0;

    /** IP address of the submitting client (audit only). */
    @Column(name = "ip_address", length = 50)
    private String ipAddress;

    /** User agent of the submitting client (audit only). */
    @Column(name = "user_agent", length = 200)
    private String userAgent;

    /** Timestamp when the request was received from the partner. */
    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    /** Timestamp when validation completed. */
    @Column(name = "validated_at")
    private Instant validatedAt;

    /** Timestamp when the request was queued for async processing. */
    @Column(name = "queued_at")
    private Instant queuedAt;

    /** Timestamp when processing completed. */
    @Column(name = "completed_at")
    private Instant completedAt;

    /** Documents referenced within this submission. */
    @OneToMany(mappedBy = "ingestionRequest")
    @Builder.Default
    private List<IngestionDocument> documents = new ArrayList<>();

}
