# 07 — Validation Engine (Module 4)

**Status:** COMPLETE

## Design Philosophy

Metadata-driven, configurable rule engine — **no insurer-specific logic is hardcoded**. All behaviour is driven by `FieldRule` and `DocumentRule` rows loaded from the database. This makes the engine reusable across motor, travel, health and future insurance categories.

## Components

### ValidationEngine (`com.insurance.integration.schema.service.ValidationEngine`)

Stateless service that evaluates a submission against a schema's rules.

**Inputs:** submissionId, ProductSchema, List<FieldRuleDTO>, List<DocumentRuleDTO>, customerData (JSON string), productData (JSON string), List<IngestionDocumentRef> (documentType, fileName, declaredMimeType — URL excluded).

**Output:** `ValidationResultDTO` with field-level errors.

### Validation Flow

1. Build a JSON root node from `customer` and `applicationData` (product_data).
2. For each `FieldRule`, resolve the dot-notation field path (tried at root, then under `applicationData`, then under `customer`).
3. Evaluate presence, type, and constraints; collect errors.
4. For each `DocumentRule`, check presence of the document type; required missing → error.
5. Detect duplicate document types.
6. Compute `totalChecks = fieldChecks + docChecks`, `failedChecks = errors.size()`, `passedChecks = totalChecks - failedChecks`.
7. Status: `VALIDATED` if 0 failures, else `VALIDATION_FAILED`.

### Field Rule Evaluation

| Data type | Checks |
|-----------|--------|
| STRING | presence, empty, maxLength, minLength, regex |
| NUMBER | presence, type, minValue, maxValue |
| DATE | presence, type, date_format parse |
| BOOLEAN | presence, type |
| ENUM | presence, allowedValues match |

Error codes: `REQUIRED_FIELD_MISSING`, `FIELD_EMPTY`, `INVALID_TYPE`, `LENGTH_ABOVE_MAX`, `LENGTH_BELOW_MIN`, `INVALID_FORMAT`, `VALUE_BELOW_MIN`, `VALUE_ABOVE_MAX`, `INVALID_DATE`, `INVALID_ENUM`.

### Document Rule Evaluation

Error codes: `REQUIRED_DOCUMENT_MISSING`, `DUPLICATE_DOCUMENT_METADATA`.

### ValidationService

Orchestrates: selects schema via `SchemaService.selectSchema`, loads field/document rules, builds document refs (URL excluded), calls `ValidationEngine`, persists result to `ingestion_requests.validation_result`, sets `validation_error_count`, `validated_at`, and updates status to `VALIDATED` / `VALIDATION_FAILED`.

## Schema Selection Precedence

For partner/insurer/product: partner+insurer > partner-only > insurer-only > global (both NULL). Exact `schemaVersion` matched when requested.

## Security

- Pre-signed source URLs are **never** passed to the validation engine (only documentType/fileName/declaredMimeType).
- Validation error messages never echo URLs or sensitive values.
- Rejected values for sensitive fields are redacted by `GlobalExceptionHandler.safeRejectedValue`.

## Test Coverage

- `ValidationEngineTest` (11 unit tests): valid submission, missing/empty fields, wrong type, numeric bounds, min/max length, regex/format, missing/duplicate documents, totals consistency, metadata.
- `SchemaServiceTest` (14 unit tests): schema CRUD, field/document rules, duplicates, not-found.
- `SchemaControllerIT` (8 integration tests): schema CRUD via HTTP.
