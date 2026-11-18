# 04 — Schema & Tag Validation (Module 4)

**Status:** COMPLETE  
**Module:** Configurable Schema and Tag Validation  
**Owner:** Backend Engineering

## Overview

Module 4 evaluates whether submitted insurance application data complies with the configured requirements of the relevant insurer and product. It integrates directly with Module 3.

## Business Objective

Different insurers and products have different information requirements. The validation engine identifies whether all required tags and values have been submitted, using metadata-driven rules (no insurer-specific hardcoded logic).

## Schema Management

| Entity | Table | Description |
|--------|-------|-------------|
| `ProductSchema` | `product_schemas` | Schema version tied to insurer/product; NULL insurer/partner = global default |
| `FieldRule` | `field_rules` | Per-field validation rule (dot-notation path, e.g. `customer.name`) |
| `DocumentRule` | `document_rules` | Required-document rule per schema |

Versioning: historical schema versions are **never modified in place**. A new version is inserted as a distinct row, so previously validated submissions remain traceable to the exact schema used.

## Field Validation Rules

| Rule | Checks |
|------|--------|
| Required | Missing/null/empty required fields |
| Type | STRING, NUMBER, DATE, BOOLEAN, ENUM |
| Length | min/max string length |
| Numeric | min/max value |
| Format | regex pattern, date format |
| Enum | allowed values |
| Presence | null vs empty string |

## Document Metadata Validation

| Rule | Checks |
|------|--------|
| Required documents | Missing mandatory document types |
| Duplicates | Duplicate document types where prohibited |
| Metadata | File name, declared MIME type |

> The engine validates request + document metadata only. It does **not** download or inspect actual S3 object binary content (Modules 6-8).

## Validation Result Structure

```json
{
  "submissionId": "550e8400-...",
  "schemaVersion": "1.0",
  "validationStatus": "VALIDATION_FAILED",
  "totalChecks": 8,
  "passedChecks": 6,
  "failedChecks": 2,
  "errors": [
    { "field": "customer.mobile", "code": "INVALID_FORMAT", "message": "Mobile number is invalid", "rejectedValue": "999" },
    { "field": "vehicle.registrationNumber", "code": "REQUIRED_FIELD_MISSING", "message": "Registration number is required", "rejectedValue": null }
  ],
  "validatedAt": "2026-11-16T10:30:00Z"
}
```

## Schema Selection Precedence

For a partner/insurer/product combination, the most specific active schema is selected:
1. partner + insurer match
2. partner-only match
3. insurer-only match
4. global default (both NULL)

## API Endpoints

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/api/v1/admin/schemas` | none (academic) | Create schema |
| GET | `/api/v1/admin/schemas` | none (academic) | List schemas (paginated, filterable) |
| GET | `/api/v1/admin/schemas/{id}` | none (academic) | Get schema |
| PUT | `/api/v1/admin/schemas/{id}` | none (academic) | Update schema name/active |
| POST | `/api/v1/admin/schemas/{id}/versions` | none (academic) | Create schema version |
| GET | `/api/v1/admin/schemas/{id}/versions` | none (academic) | List schema versions |
| GET | `/api/v1/admin/schemas/{id}/rules` | none (academic) | List field rules |
| POST | `/api/v1/admin/schemas/{id}/rules` | none (academic) | Create field rule |
| GET | `/api/v1/admin/schemas/{id}/document-rules` | none (academic) | List document rules |
| POST | `/api/v1/admin/schemas/{id}/document-rules` | none (academic) | Create document rule |
| POST | `/api/v1/admin/insurance/submissions/{id}/validate` | none (academic) | Trigger validation |
| GET | `/api/v1/partner/insurance/submissions/{id}/validation` | JWT `read` | Get validation result (partner) |
| GET | `/api/v1/partner/insurance/submissions/admin/{id}/validation` | none (academic) | Get validation result (admin) |

## Module 3 ↔ Module 4 Integration

```
Partner Request -> Ingest -> Persist (RECEIVED) -> Select Schema -> Validate -> Persist Result -> Update Status (VALIDATED/VALIDATION_FAILED) -> Return Response
```

If validation fails, the submission and its validation results are preserved for audit and partner correction/resubmission.

## Test Coverage

- **SchemaService unit:** 14 (`SchemaServiceTest`) — create/get/list/update schema, field/document rules, duplicates, not-found.
- **ValidationEngine unit:** 11 (`ValidationEngineTest`) — valid submission, missing/empty fields, wrong type, numeric bounds, regex/format, missing/duplicate documents, totals consistency.
- **SchemaController integration:** 8 (`SchemaControllerIT`) — full schema CRUD via HTTP.
