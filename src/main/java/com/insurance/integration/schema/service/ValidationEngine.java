package com.insurance.integration.schema.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.insurance.integration.schema.dto.DocumentRuleDTO;
import com.insurance.integration.schema.dto.FieldRuleDTO;
import com.insurance.integration.schema.dto.ValidationErrorDTO;
import com.insurance.integration.schema.dto.ValidationResultDTO;
import com.insurance.integration.schema.entity.ProductSchema;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Configurable, metadata-driven validation engine (Module 4).
 *
 * <p>Evaluates {@link FieldRuleDTO} and {@link DocumentRuleDTO} rules against a submitted
 * insurance application. No insurer-specific logic is hardcoded — all behaviour is driven
 * by the rules loaded from the database.</p>
 */
@Service
@Slf4j
public class ValidationEngine {

    private static final String DATE_FORMAT_DEFAULT = "yyyy-MM-dd";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Evaluate a submission against a schema's field and document rules.
     *
     * @param submissionId the ingestion request being validated
     * @param schema the schema version to validate against
     * @param fieldRules the field rules for the schema
     * @param documentRules the document rules for the schema
     * @param customerData serialised customer JSON (may be null)
     * @param productData serialised product/application JSON (may be null)
     * @param documents document metadata references (may be null)
     * @return a structured validation result
     */
    public ValidationResultDTO validate(UUID submissionId, ProductSchema schema,
                                        List<FieldRuleDTO> fieldRules,
                                        List<DocumentRuleDTO> documentRules,
                                        String customerData, String productData,
                                        List<IngestionDocumentRef> documents) {

        JsonNode root = buildRoot(customerData, productData);

        List<ValidationErrorDTO> errors = new ArrayList<>();

        // ─── field validation ────────────────────────────────────────────
        int fieldChecks = 0;
        for (FieldRuleDTO rule : fieldRules) {
            fieldChecks++;
            JsonNode value = resolveValue(root, rule.getFieldPath());
            evaluateField(rule, value, errors);
        }

        // ─── document validation ─────────────────────────────────────────
        int docChecks = 0;
        Set<String> seenDocumentTypes = new HashSet<>();
        for (DocumentRuleDTO rule : documentRules) {
            docChecks++;
            boolean present = hasDocument(documents, rule.getDocumentType(), seenDocumentTypes);
            if (!present && rule.isRequired()) {
                errors.add(ValidationErrorDTO.builder()
                        .field("documents." + rule.getDocumentType())
                        .code("REQUIRED_DOCUMENT_MISSING")
                        .message("Required document '" + rule.getDocumentType() + "' is missing")
                        .rejectedValue(null)
                        .build());
            }
        }
        // duplicate document-type detection
        Set<String> duplicates = findDuplicateDocumentTypes(documents);
        for (String dup : duplicates) {
            docChecks++;
            errors.add(ValidationErrorDTO.builder()
                    .field("documents." + dup)
                    .code("DUPLICATE_DOCUMENT_METADATA")
                    .message("Duplicate document references for type '" + dup + "'")
                    .rejectedValue(dup)
                    .build());
        }

        int totalChecks = fieldChecks + docChecks;
        int failedChecks = errors.size();
        int passedChecks = totalChecks - failedChecks;
        String status = failedChecks == 0 ? "VALIDATED" : "VALIDATION_FAILED";

        log.info("Validation submission={} schema={} version={} checks={} failed={} status={}",
                submissionId, schema.getId(), schema.getSchemaVersion(), totalChecks, failedChecks, status);

        return ValidationResultDTO.builder()
                .submissionId(submissionId)
                .schemaVersion(schema.getSchemaVersion())
                .validationStatus(status)
                .totalChecks(totalChecks)
                .passedChecks(passedChecks)
                .failedChecks(failedChecks)
                .errors(errors)
                .validatedAt(Instant.now())
                .build();
    }

    // ─── field evaluation ────────────────────────────────────────────────

    private void evaluateField(FieldRuleDTO rule, JsonNode value, List<ValidationErrorDTO> errors) {
        String field = rule.getFieldPath();

        // Absence checks
        boolean absent = value == null || value.isMissingNode() || value.isNull();
        if (absent) {
            if (rule.isRequired()) {
                errors.add(error(rule, "REQUIRED_FIELD_MISSING",
                        "Required field '" + rule.getDisplayName() + "' is missing", null));
            }
            return;
        }

        // Empty string check for required string fields
        if (value.isTextual() && value.asText().trim().isEmpty()) {
            if (rule.isRequired()) {
                errors.add(error(rule, "FIELD_EMPTY",
                        "Required field '" + rule.getDisplayName() + "' is empty", value.asText()));
            }
            return;
        }

        // Data-type specific checks
        switch (rule.getDataType()) {
            case STRING -> evaluateString(rule, value, errors);
            case NUMBER -> evaluateNumber(rule, value, errors);
            case DATE -> evaluateDate(rule, value, errors);
            case BOOLEAN -> evaluateBoolean(rule, value, errors);
            case ENUM -> evaluateEnum(rule, value, errors);
            default -> {
                // No type-specific constraint beyond presence
            }
        }
    }

    private void evaluateString(FieldRuleDTO rule, JsonNode value, List<ValidationErrorDTO> errors) {
        if (!value.isTextual()) {
            errors.add(error(rule, "INVALID_TYPE",
                    "Field '" + rule.getDisplayName() + "' must be a string", value.toString()));
            return;
        }
        String text = value.asText();
        if (rule.getMaxLength() != null && text.length() > rule.getMaxLength()) {
            errors.add(error(rule, "LENGTH_ABOVE_MAX",
                    "Field '" + rule.getDisplayName() + "' exceeds maximum length of " + rule.getMaxLength(), text));
        }
        if (rule.getMinLength() != null && text.length() < rule.getMinLength()) {
            errors.add(error(rule, "LENGTH_BELOW_MIN",
                    "Field '" + rule.getDisplayName() + "' is below minimum length of " + rule.getMinLength(), text));
        }
        if (rule.getRegexPattern() != null && !rule.getRegexPattern().isBlank()) {
            if (!Pattern.compile(rule.getRegexPattern()).matcher(text).matches()) {
                errors.add(error(rule, "INVALID_FORMAT",
                        "Field '" + rule.getDisplayName() + "' is in an invalid format", text));
            }
        }
    }

    private void evaluateNumber(FieldRuleDTO rule, JsonNode value, List<ValidationErrorDTO> errors) {
        if (!value.isNumber()) {
            errors.add(error(rule, "INVALID_TYPE",
                    "Field '" + rule.getDisplayName() + "' must be a number", value.toString()));
            return;
        }
        java.math.BigDecimal num = value.decimalValue();
        if (rule.getMinValue() != null && num.compareTo(rule.getMinValue()) < 0) {
            errors.add(error(rule, "VALUE_BELOW_MIN",
                    "Field '" + rule.getDisplayName() + "' is below minimum value of " + rule.getMinValue(), num.toString()));
        }
        if (rule.getMaxValue() != null && num.compareTo(rule.getMaxValue()) > 0) {
            errors.add(error(rule, "VALUE_ABOVE_MAX",
                    "Field '" + rule.getDisplayName() + "' exceeds maximum value of " + rule.getMaxValue(), num.toString()));
        }
    }

    private void evaluateDate(FieldRuleDTO rule, JsonNode value, List<ValidationErrorDTO> errors) {
        if (!value.isTextual()) {
            errors.add(error(rule, "INVALID_TYPE",
                    "Field '" + rule.getDisplayName() + "' must be a date string", value.toString()));
            return;
        }
        String format = rule.getDateFormat() != null && !rule.getDateFormat().isBlank()
                ? rule.getDateFormat() : DATE_FORMAT_DEFAULT;
        try {
            DateTimeFormatter.ofPattern(format).parse(value.asText());
        } catch (Exception e) {
            errors.add(error(rule, "INVALID_DATE",
                    "Field '" + rule.getDisplayName() + "' is not a valid date (expected " + format + ")",
                    value.asText()));
        }
    }

    private void evaluateBoolean(FieldRuleDTO rule, JsonNode value, List<ValidationErrorDTO> errors) {
        if (!value.isBoolean()) {
            errors.add(error(rule, "INVALID_TYPE",
                    "Field '" + rule.getDisplayName() + "' must be a boolean", value.toString()));
        }
    }

    private void evaluateEnum(FieldRuleDTO rule, JsonNode value, List<ValidationErrorDTO> errors) {
        if (rule.getAllowedValues() == null || rule.getAllowedValues().isBlank()) {
            return; // no allowed values configured
        }
        String text = value.isTextual() ? value.asText() : value.toString();
        boolean allowed = false;
        for (String part : rule.getAllowedValues().split(",")) {
            String option = part.trim().replaceAll("^\"|\"$", "");
            if (option.equalsIgnoreCase(text)) {
                allowed = true;
                break;
            }
        }
        if (!allowed) {
            errors.add(error(rule, "INVALID_ENUM",
                    "Field '" + rule.getDisplayName() + "' has an invalid value", text));
        }
    }

    // ─── helpers ─────────────────────────────────────────────────────────

    private JsonNode buildRoot(String customerData, String productData) {
        ObjectNode root = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
        if (customerData != null && !customerData.isBlank()) {
            try {
                root.set("customer", MAPPER.readTree(customerData));
            } catch (Exception e) {
                root.putObject("customer");
            }
        } else {
            root.putObject("customer");
        }
        if (productData != null && !productData.isBlank()) {
            try {
                root.set("applicationData", MAPPER.readTree(productData));
            } catch (Exception e) {
                root.putObject("applicationData");
            }
        } else {
            root.putObject("applicationData");
        }
        return root;
    }

    private JsonNode resolveValue(JsonNode root, String fieldPath) {
        // Resolve a dot-notation path (e.g. customer.name or vehicle.registrationNumber)
        // against the submission root. The field may live at the top level, under
        // applicationData, or under customer.
        JsonNode node = traverse(root, fieldPath);
        if (node != null && !node.isMissingNode() && !node.isNull()) {
            return node;
        }
        node = traverse(root, "applicationData." + fieldPath);
        if (node != null && !node.isMissingNode() && !node.isNull()) {
            return node;
        }
        node = traverse(root, "customer." + fieldPath);
        if (node != null && !node.isMissingNode() && !node.isNull()) {
            return node;
        }
        return null;
    }

    private JsonNode traverse(JsonNode root, String dottedPath) {
        JsonNode cur = root;
        for (String part : dottedPath.split("\\.")) {
            if (cur == null || cur.isMissingNode() || !cur.isObject()) {
                return null;
            }
            cur = cur.path(part);
        }
        return cur;
    }

    private boolean hasDocument(List<IngestionDocumentRef> documents, String documentType,
                                Set<String> seen) {
        if (documents == null) return false;
        boolean found = false;
        for (IngestionDocumentRef doc : documents) {
            if (documentType.equals(doc.documentType)) {
                found = true;
                seen.add(documentType);
            }
        }
        return found;
    }

    private Set<String> findDuplicateDocumentTypes(List<IngestionDocumentRef> documents) {
        Set<String> duplicates = new HashSet<>();
        Set<String> seen = new HashSet<>();
        if (documents == null) return duplicates;
        for (IngestionDocumentRef doc : documents) {
            if (!seen.add(doc.documentType)) {
                duplicates.add(doc.documentType);
            }
        }
        return duplicates;
    }

    private ValidationErrorDTO error(FieldRuleDTO rule, String code, String message,
                                     Object rejectedValue) {
        return ValidationErrorDTO.builder()
                .field(rule.getFieldPath())
                .code(code)
                .message(message)
                .rejectedValue(rejectedValue)
                .build();
    }

    /**
     * Lightweight reference to a submitted document for validation purposes.
     * The pre-signed URL is intentionally NOT included.
     */
    public record IngestionDocumentRef(String documentType, String fileName, String declaredMimeType) {
    }
}
