package com.insurance.integration.schema.entity;

import com.insurance.integration.common.entity.BaseEntity;
import com.insurance.integration.common.enums.FieldDataType;
import jakarta.persistence.Access;
import jakarta.persistence.AccessType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Per-field validation rule within a product schema (Module 4 - Schema Validation).
 *
 * <p>Maps to the {@code field_rules} table created by Flyway migration
 * {@code V3__Create_schema_engine.sql}. The {@code field_path} uses dot notation into the
 * request JSON (e.g. {@code customer.name} or {@code vehicle.registrationNumber}).</p>
 */
@Entity
@Access(AccessType.FIELD)
@Table(name = "field_rules")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FieldRule extends BaseEntity {

    /** Schema this rule belongs to. */
    @Column(name = "schema_id", nullable = false)
    @JdbcTypeCode(SqlTypes.UUID)
    private UUID schemaId;

    /** Dot-notation path into the submitted JSON, e.g. customer.name or vehicle.make. */
    @Column(name = "field_path", length = 200, nullable = false)
    private String fieldPath;

    /** Human-readable label for the field. */
    @Column(name = "display_name", length = 200, nullable = false)
    private String displayName;

    /** Whether the field is mandatory. */
    @Column(name = "is_required", nullable = false)
    @Builder.Default
    private boolean required = true;

    /** Expected data type (STRING, NUMBER, DATE, BOOLEAN, ENUM). */
    @Column(name = "data_type", length = 50, nullable = false)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private FieldDataType dataType = FieldDataType.STRING;

    /** Minimum permitted string length. */
    @Column(name = "min_length")
    private Integer minLength;

    /** Maximum permitted string length. */
    @Column(name = "max_length")
    private Integer maxLength;

    /** Minimum permitted numeric value. */
    @Column(name = "min_value", precision = 19, scale = 4)
    private BigDecimal minValue;

    /** Maximum permitted numeric value. */
    @Column(name = "max_value", precision = 19, scale = 4)
    private BigDecimal maxValue;

    /** Regular expression the string value must match. */
    @Column(name = "regex_pattern", length = 500)
    private String regexPattern;

    /** JSON array of permitted values for ENUM-typed fields. */
    @Column(name = "allowed_values", columnDefinition = "TEXT")
    private String allowedValues;

    /** Expected date format (e.g. yyyy-MM-dd). */
    @Column(name = "date_format", length = 50)
    private String dateFormat;

    /** Custom error message to return when this rule fails. */
    @Column(name = "error_message", length = 500)
    private String errorMessage;
}
