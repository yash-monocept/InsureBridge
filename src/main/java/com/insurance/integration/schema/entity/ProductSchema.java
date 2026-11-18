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
 * Configurable validation schema for an insurance product (Module 4 - Schema Validation).
 *
 * <p>Maps to the {@code product_schemas} table created by Flyway migration
 * {@code V3__Create_schema_engine.sql}. A schema is associated with an insurer and/or
 * product type; {@code insurer_id}/{@code partner_id} being NULL means a global default.</p>
 */
@Entity
@Access(AccessType.FIELD)
@Table(name = "product_schemas")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductSchema extends BaseEntity {

    /** Insurer this schema belongs to (NULL = global schema). */
    @Column(name = "insurer_id")
    @JdbcTypeCode(SqlTypes.UUID)
    private UUID insurerId;

    /** Partner this schema belongs to (NULL = global schema). */
    @Column(name = "partner_id")
    @JdbcTypeCode(SqlTypes.UUID)
    private UUID partnerId;

    /** Insurance product type (e.g. MOTOR_INSURANCE, TRAVEL_INSURANCE, HEALTH_INSURANCE). */
    @Column(name = "product_type", length = 100, nullable = false)
    private String productType;

    /** Schema version (e.g. 1.0). Historical versions are never modified. */
    @Column(name = "schema_version", length = 20, nullable = false)
    private String schemaVersion;

    /** Human-readable schema name. */
    @Column(name = "schema_name", length = 200, nullable = false)
    private String schemaName;

    /** Whether this schema version is active and usable for new validations. */
    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;
}
