package com.insurance.integration.unit.schema;

import com.insurance.integration.schema.dto.FieldRuleDTO;
import com.insurance.integration.schema.dto.DocumentRuleDTO;
import com.insurance.integration.schema.dto.ValidationResultDTO;
import com.insurance.integration.schema.entity.ProductSchema;
import com.insurance.integration.schema.service.ValidationEngine;
import com.insurance.integration.common.enums.FieldDataType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the Module 4 {@link ValidationEngine}.
 * All behaviour is driven by configurable field and document rules — no
 * insurer-specific logic is hardcoded.
 */
@DisplayName("ValidationEngine Unit Tests")
class ValidationEngineTest {

    private ProductSchema motorSchema() {
        return ProductSchema.builder()
                .productType("MOTOR_INSURANCE")
                .schemaVersion("1.0")
                .schemaName("Motor Insurance Schema v1.0")
                .active(true)
                .build();
    }

    private List<FieldRuleDTO> motorFieldRules() {
        return List.of(
                fieldRule("customer.name", "Customer Full Name", true, FieldDataType.STRING, null, 200, null, null, null, null, "Customer name is required"),
                fieldRule("customer.mobile", "Customer Mobile Number", true, FieldDataType.STRING, null, 15, null, null, null, null, "Customer mobile is required"),
                fieldRule("vehicle.make", "Vehicle Make", true, FieldDataType.STRING, null, 100, null, null, null, null, "Vehicle make is required"),
                fieldRule("vehicle.model", "Vehicle Model", true, FieldDataType.STRING, null, 100, null, null, null, null, "Vehicle model is required"),
                fieldRule("vehicle.registrationNumber", "Vehicle Registration Number", true, FieldDataType.STRING, null, 20, null, null, null, null, "Registration number is required"),
                fieldRule("vehicle.manufacturingYear", "Manufacturing Year", false, FieldDataType.NUMBER, null, null,
                        BigDecimal.valueOf(1900), BigDecimal.valueOf(2100), null, null, null),
                fieldRule("customer.email", "Customer Email", false, FieldDataType.STRING, null, 200, null, null, "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$", null, null)
        );
    }

    private List<DocumentRuleDTO> motorDocumentRules() {
        return List.of(
                documentRule("IDENTITY_PROOF", true, "[\"application/pdf\",\"image/jpeg\",\"image/png\"]", 10, "Identity proof is required"),
                documentRule("VEHICLE_DOCUMENT", true, "[\"application/pdf\",\"image/jpeg\",\"image/png\"]", 10, "Vehicle document is required")
        );
    }

    private FieldRuleDTO fieldRule(String fieldPath, String displayName, boolean required, FieldDataType dataType,
                                   Integer minLength, Integer maxLength, BigDecimal minValue, BigDecimal maxValue,
                                   String regexPattern, String allowedValues, String errorMessage) {
        return FieldRuleDTO.builder()
                .fieldPath(fieldPath).displayName(displayName).required(required).dataType(dataType)
                .minLength(minLength).maxLength(maxLength).minValue(minValue).maxValue(maxValue)
                .regexPattern(regexPattern).allowedValues(allowedValues).errorMessage(errorMessage)
                .build();
    }

    private DocumentRuleDTO documentRule(String documentType, boolean required, String allowedMimeTypes,
                                         int maxSizeMb, String description) {
        return DocumentRuleDTO.builder()
                .documentType(documentType).required(required).allowedMimeTypes(allowedMimeTypes)
                .maxSizeMb(maxSizeMb).description(description).build();
    }

    // ─── happy path ─────────────────────────────────────────────────────

    @Test
    @DisplayName("valid motor submission passes all checks")
    void validSubmission_passesAllChecks() {
        var engine = new ValidationEngine();
        String customer = "{\"name\":\"Rahul Sharma\",\"mobile\":\"9999999999\",\"email\":\"rahul@example.com\"}";
        String product = "{\"vehicle\":{\"make\":\"Hyundai\",\"model\":\"Creta\",\"registrationNumber\":\"DL01AB1234\",\"manufacturingYear\":2026}}";
        var docs = List.of(
                new ValidationEngine.IngestionDocumentRef("IDENTITY_PROOF", "identity.pdf", "application/pdf"),
                new ValidationEngine.IngestionDocumentRef("VEHICLE_DOCUMENT", "vehicle.pdf", "application/pdf"));

        ValidationResultDTO result = engine.validate(
                UUID.randomUUID(), motorSchema(), motorFieldRules(), motorDocumentRules(),
                customer, product, docs);

        assertThat(result.getValidationStatus()).isEqualTo("VALIDATED");
        assertThat(result.getFailedChecks()).isZero();
        assertThat(result.getTotalChecks()).isEqualTo(motorFieldRules().size() + motorDocumentRules().size());
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValidatedAt()).isNotNull();
    }

    // ─── missing required fields ────────────────────────────────────────

    @Test
    @DisplayName("missing required field produces REQUIRED_FIELD_MISSING error")
    void missingRequiredField_reportsMissing() {
        var engine = new ValidationEngine();
        String customer = "{}";
        String product = "{\"vehicle\":{}}";
        var docs = List.of(
                new ValidationEngine.IngestionDocumentRef("IDENTITY_PROOF", "identity.pdf", "application/pdf"),
                new ValidationEngine.IngestionDocumentRef("VEHICLE_DOCUMENT", "vehicle.pdf", "application/pdf"));

        ValidationResultDTO result = engine.validate(
                UUID.randomUUID(), motorSchema(), motorFieldRules(), motorDocumentRules(),
                customer, product, docs);

        assertThat(result.getValidationStatus()).isEqualTo("VALIDATION_FAILED");
        assertThat(result.getFailedChecks()).isGreaterThan(0);
        assertThat(result.getErrors())
                .anyMatch(e -> e.getField().equals("customer.name") && e.getCode().equals("REQUIRED_FIELD_MISSING"))
                .anyMatch(e -> e.getField().equals("vehicle.make") && e.getCode().equals("REQUIRED_FIELD_MISSING"));
    }

    @Test
    @DisplayName("empty string required field produces FIELD_EMPTY error")
    void emptyRequiredField_reportsEmpty() {
        var engine = new ValidationEngine();
        String customer = "{\"name\":\"\",\"mobile\":\"9999999999\",\"email\":\"rahul@example.com\"}";
        String product = "{\"vehicle\":{\"make\":\"Hyundai\",\"model\":\"Creta\",\"registrationNumber\":\"DL01AB1234\"}}";
        var docs = List.of(
                new ValidationEngine.IngestionDocumentRef("IDENTITY_PROOF", "identity.pdf", "application/pdf"),
                new ValidationEngine.IngestionDocumentRef("VEHICLE_DOCUMENT", "vehicle.pdf", "application/pdf"));

        ValidationResultDTO result = engine.validate(
                UUID.randomUUID(), motorSchema(), motorFieldRules(), motorDocumentRules(),
                customer, product, docs);

        assertThat(result.getValidationStatus()).isEqualTo("VALIDATION_FAILED");
        assertThat(result.getErrors())
                .anyMatch(e -> e.getField().equals("customer.name") && e.getCode().equals("FIELD_EMPTY"));
    }

    // ─── type and constraint checks ─────────────────────────────────────

    @Test
    @DisplayName("wrong data type produces INVALID_TYPE error")
    void wrongType_reportsInvalidType() {
        var engine = new ValidationEngine();
        String customer = "{\"name\":\"Rahul\",\"mobile\":\"9999999999\"}";
        String product = "{\"vehicle\":{\"make\":123,\"model\":\"Creta\",\"registrationNumber\":\"DL01AB1234\"}}";
        var docs = List.of(
                new ValidationEngine.IngestionDocumentRef("IDENTITY_PROOF", "identity.pdf", "application/pdf"),
                new ValidationEngine.IngestionDocumentRef("VEHICLE_DOCUMENT", "vehicle.pdf", "application/pdf"));

        ValidationResultDTO result = engine.validate(
                UUID.randomUUID(), motorSchema(), motorFieldRules(), motorDocumentRules(),
                customer, product, docs);

        assertThat(result.getValidationStatus()).isEqualTo("VALIDATION_FAILED");
        assertThat(result.getErrors())
                .anyMatch(e -> e.getField().equals("vehicle.make") && e.getCode().equals("INVALID_TYPE"));
    }

    @Test
    @DisplayName("numeric below min produces VALUE_BELOW_MIN error")
    void numericBelowMin_reportsBelowMin() {
        var engine = new ValidationEngine();
        String customer = "{\"name\":\"Rahul\",\"mobile\":\"9999999999\"}";
        String product = "{\"vehicle\":{\"make\":\"Hyundai\",\"model\":\"Creta\",\"registrationNumber\":\"DL01AB1234\",\"manufacturingYear\":1800}}";
        var docs = List.of(
                new ValidationEngine.IngestionDocumentRef("IDENTITY_PROOF", "identity.pdf", "application/pdf"),
                new ValidationEngine.IngestionDocumentRef("VEHICLE_DOCUMENT", "vehicle.pdf", "application/pdf"));

        ValidationResultDTO result = engine.validate(
                UUID.randomUUID(), motorSchema(), motorFieldRules(), motorDocumentRules(),
                customer, product, docs);

        assertThat(result.getValidationStatus()).isEqualTo("VALIDATION_FAILED");
        assertThat(result.getErrors())
                .anyMatch(e -> e.getField().equals("vehicle.manufacturingYear") && e.getCode().equals("VALUE_BELOW_MIN"));
    }

    @Test
    @DisplayName("string exceeding max length produces LENGTH_ABOVE_MAX error")
    void stringTooLong_reportsAboveMax() {
        var engine = new ValidationEngine();
        String customer = "{\"name\":\"Rahul Sharma\",\"mobile\":\"9999999999\",\"email\":\"rahul@example.com\"}";
        String product = "{\"vehicle\":{\"make\":\"" + "A".repeat(201) + "\",\"model\":\"Creta\",\"registrationNumber\":\"DL01AB1234\",\"manufacturingYear\":2026}}";
        var docs = List.of(
                new ValidationEngine.IngestionDocumentRef("IDENTITY_PROOF", "identity.pdf", "application/pdf"),
                new ValidationEngine.IngestionDocumentRef("VEHICLE_DOCUMENT", "vehicle.pdf", "application/pdf"));

        ValidationResultDTO result = engine.validate(
                UUID.randomUUID(), motorSchema(), motorFieldRules(), motorDocumentRules(),
                customer, product, docs);

        assertThat(result.getValidationStatus()).isEqualTo("VALIDATION_FAILED");
        assertThat(result.getErrors())
                .anyMatch(e -> e.getField().equals("vehicle.make") && e.getCode().equals("LENGTH_ABOVE_MAX"));
    }

    // ─── regex / format checks ──────────────────────────────────────────

    @Test
    @DisplayName("value failing regex produces INVALID_FORMAT error")
    void failingRegex_reportsInvalidFormat() {
        var engine = new ValidationEngine();
        String customer = "{\"name\":\"Rahul\",\"mobile\":\"9999999999\",\"email\":\"not-an-email\"}";
        String product = "{\"vehicle\":{\"make\":\"Hyundai\",\"model\":\"Creta\",\"registrationNumber\":\"DL01AB1234\"}}";
        var docs = List.of(
                new ValidationEngine.IngestionDocumentRef("IDENTITY_PROOF", "identity.pdf", "application/pdf"),
                new ValidationEngine.IngestionDocumentRef("VEHICLE_DOCUMENT", "vehicle.pdf", "application/pdf"));

        ValidationResultDTO result = engine.validate(
                UUID.randomUUID(), motorSchema(), motorFieldRules(), motorDocumentRules(),
                customer, product, docs);

        assertThat(result.getValidationStatus()).isEqualTo("VALIDATION_FAILED");
        assertThat(result.getErrors())
                .anyMatch(e -> e.getField().equals("customer.email") && e.getCode().equals("INVALID_FORMAT"));
    }

    // ─── document checks ────────────────────────────────────────────────

    @Test
    @DisplayName("missing required document produces REQUIRED_DOCUMENT_MISSING error")
    void missingRequiredDocument_reportsMissing() {
        var engine = new ValidationEngine();
        String customer = "{\"name\":\"Rahul\",\"mobile\":\"9999999999\"}";
        String product = "{\"vehicle\":{\"make\":\"Hyundai\",\"model\":\"Creta\",\"registrationNumber\":\"DL01AB1234\"}}";
        var docs = List.of(
                new ValidationEngine.IngestionDocumentRef("IDENTITY_PROOF", "identity.pdf", "application/pdf"));

        ValidationResultDTO result = engine.validate(
                UUID.randomUUID(), motorSchema(), motorFieldRules(), motorDocumentRules(),
                customer, product, docs);

        assertThat(result.getValidationStatus()).isEqualTo("VALIDATION_FAILED");
        assertThat(result.getErrors())
                .anyMatch(e -> e.getField().equals("documents.VEHICLE_DOCUMENT") && e.getCode().equals("REQUIRED_DOCUMENT_MISSING"));
    }

    @Test
    @DisplayName("duplicate document type produces DUPLICATE_DOCUMENT_METADATA error")
    void duplicateDocument_reportsDuplicate() {
        var engine = new ValidationEngine();
        String customer = "{\"name\":\"Rahul\",\"mobile\":\"9999999999\"}";
        String product = "{\"vehicle\":{\"make\":\"Hyundai\",\"model\":\"Creta\",\"registrationNumber\":\"DL01AB1234\"}}";
        var docs = List.of(
                new ValidationEngine.IngestionDocumentRef("IDENTITY_PROOF", "identity.pdf", "application/pdf"),
                new ValidationEngine.IngestionDocumentRef("IDENTITY_PROOF", "identity2.pdf", "application/pdf"),
                new ValidationEngine.IngestionDocumentRef("VEHICLE_DOCUMENT", "vehicle.pdf", "application/pdf"));

        ValidationResultDTO result = engine.validate(
                UUID.randomUUID(), motorSchema(), motorFieldRules(), motorDocumentRules(),
                customer, product, docs);

        assertThat(result.getValidationStatus()).isEqualTo("VALIDATION_FAILED");
        assertThat(result.getErrors())
                .anyMatch(e -> e.getCode().equals("DUPLICATE_DOCUMENT_METADATA"));
    }

    // ─── totals ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("totals equal passed plus failed checks")
    void totalsConsistent() {
        var engine = new ValidationEngine();
        String customer = "{}";
        String product = "{\"vehicle\":{}}";
        List<ValidationEngine.IngestionDocumentRef> docs = List.of();

        ValidationResultDTO result = engine.validate(
                UUID.randomUUID(), motorSchema(), motorFieldRules(), motorDocumentRules(),
                customer, product, docs);

        assertThat(result.getTotalChecks())
                .isEqualTo(result.getPassedChecks() + result.getFailedChecks());
        assertThat(result.getFailedChecks()).isGreaterThan(0);
        assertThat(result.getPassedChecks()).isGreaterThanOrEqualTo(0);
    }

    @Test
    @DisplayName("validation result carries schema version and timestamp")
    void carriesMetadata() {
        var engine = new ValidationEngine();
        String customer = "{\"name\":\"Rahul\",\"mobile\":\"9999999999\"}";
        String product = "{\"vehicle\":{\"make\":\"Hyundai\",\"model\":\"Creta\",\"registrationNumber\":\"DL01AB1234\"}}";
        var docs = List.of(
                new ValidationEngine.IngestionDocumentRef("IDENTITY_PROOF", "identity.pdf", "application/pdf"),
                new ValidationEngine.IngestionDocumentRef("VEHICLE_DOCUMENT", "vehicle.pdf", "application/pdf"));

        ValidationResultDTO result = engine.validate(
                UUID.randomUUID(), motorSchema(), motorFieldRules(), motorDocumentRules(),
                customer, product, docs);

        assertThat(result.getSchemaVersion()).isEqualTo("1.0");
        assertThat(result.getSubmissionId()).isNotNull();
        assertThat(result.getValidatedAt()).isNotNull();
        assertThat(result.getValidatedAt()).isBeforeOrEqualTo(Instant.now().plusSeconds(5));
    }
}
