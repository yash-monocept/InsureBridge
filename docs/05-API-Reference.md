# 05 — API Reference

**Status:** COMPLETE (updated for Modules 3 & 4)

## Base URL
`http://localhost:8080`

## Authentication

Partner endpoints require a JWT access token obtained via `POST /api/v1/auth/token` (Module 2). Include as `Authorization: Bearer <token>`.

## Module 3 — Insurance Submissions (Partner)

### POST /api/v1/partner/insurance/submissions
Submit an insurance application. Requires `SCOPE_write`.

**Request body** (`IngestionRequestDTO`):
```json
{
  "partnerReferenceId": "CAR-92831",
  "insurerCode": "MAX_LIFE",
  "productCode": "MOTOR_INSURANCE",
  "schemaVersion": "1.0",
  "customer": { "name": "...", "mobile": "...", "email": "..." },
  "applicationData": { "vehicle": { ... } },
  "documents": [ { "documentType": "IDENTITY_PROOF", "fileName": "...", "contentType": "application/pdf", "sourceUrl": "..." } ],
  "idempotencyKey": "idem_..."
}
```

**Success:** `201 Created` → `IngestionResponseDTO`
**Errors:** `400` validation, `401` auth, `403` not authorised, `409` duplicate idempotency key

### GET /api/v1/partner/insurance/submissions
List the authenticated partner's submissions (paginated). Requires `SCOPE_read`.

Query params: `page`, `size`, `insurerCode`, `status`, `productType`, `search`

**Success:** `200 OK` → `PagedResponse<SubmissionSummaryDTO>`

### GET /api/v1/partner/insurance/submissions/{submissionId}
Get submission detail for the authenticated partner. Requires `SCOPE_read`.

**Success:** `200 OK` → `SubmissionDetailDTO`

### GET /api/v1/partner/insurance/submissions/{submissionId}/validation
Get validation result. Requires `SCOPE_read`.

**Success:** `200 OK` → `ValidationResultDTO`

## Module 3 — Admin Submission APIs

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/v1/partner/insurance/submissions/admin/all` | List all submissions (paginated) |
| GET | `/api/v1/partner/insurance/submissions/admin/{id}` | Get any submission |
| GET | `/api/v1/partner/insurance/submissions/admin/{id}/validation` | Get any validation result |

## Module 4 — Schema Management (Admin)

### POST /api/v1/admin/schemas
Create a schema. Body: `SchemaCreateRequest` (`productType`, `schemaVersion`, `schemaName`, `active`, optional `insurerId`, `partnerId`).

**Success:** `201 Created` → `SchemaDTO`

### GET /api/v1/admin/schemas
List schemas (paginated, filterable by `insurerId`, `partnerId`, `productType`, `active`, `search`).

**Success:** `200 OK` → `PagedResponse<SchemaDTO>`

### GET /api/v1/admin/schemas/{schemaId}
Get a schema. **Success:** `200 OK` → `SchemaDTO`

### PUT /api/v1/admin/schemas/{schemaId}
Update schema name/active. Body: `SchemaUpdateRequest`.

**Success:** `200 OK` → `SchemaDTO`

### POST /api/v1/admin/schemas/{schemaId}/versions
Create a new schema version. Body: `SchemaCreateRequest`.

**Success:** `201 Created` → `SchemaDTO`

### GET /api/v1/admin/schemas/{schemaId}/versions
List all versions for the schema's product type.

**Success:** `200 OK` → `ApiResponse<List<SchemaDTO>>`

### POST /api/v1/admin/schemas/{schemaId}/rules
Create a field rule. Body: `FieldRuleCreateRequest`.

**Success:** `201 Created` → `FieldRuleDTO`

### GET /api/v1/admin/schemas/{schemaId}/rules
List field rules. **Success:** `200 OK` → `ApiResponse<List<FieldRuleDTO>>`

### POST /api/v1/admin/schemas/{schemaId}/document-rules
Create a document rule. Body: `DocumentRuleCreateRequest`.

**Success:** `201 Created` → `DocumentRuleDTO`

### GET /api/v1/admin/schemas/{schemaId}/document-rules
List document rules. **Success:** `200 OK` → `ApiResponse<List<DocumentRuleDTO>>`

## Module 4 — Validation Trigger (Admin)

### POST /api/v1/admin/insurance/submissions/{submissionId}/validate
Re-run schema validation on a submission and persist the result.

**Success:** `200 OK` → `ApiResponse<ValidationResultDTO>`

## DTO Reference

**IngestionResponseDTO:** `submissionId`, `externalReference`, `idempotencyKey`, `status`, `partnerId`, `insurerCode`, `productCode`, `schemaVersion`, `validationStatus`, `documentCount`, `receivedAt`, `message`, `documents` (sanitised, URLs excluded)

**SubmissionSummaryDTO:** `submissionId`, `externalReference`, `partnerName`, `insurerCode`, `productCode`, `receivedAt`, `validationStatus`, `processingStatus`, `validationErrorCount`, `documentCount`

**SubmissionDetailDTO:** full detail incl. `customer`, `applicationData`, `documents`, `validationResult`, `processingHistory`

**ValidationResultDTO:** `submissionId`, `schemaVersion`, `validationStatus`, `totalChecks`, `passedChecks`, `failedChecks`, `errors[]`, `validatedAt`

**ValidationErrorDTO:** `field`, `code`, `message`, `rejectedValue`

**SchemaDTO:** `id`, `insurerId`, `partnerId`, `productType`, `schemaVersion`, `schemaName`, `active`, `createdAt`, `updatedAt`

**FieldRuleDTO:** `id`, `schemaId`, `fieldPath`, `displayName`, `required`, `dataType`, `minLength`, `maxLength`, `minValue`, `maxValue`, `regexPattern`, `allowedValues`, `dateFormat`, `errorMessage`

**DocumentRuleDTO:** `id`, `schemaId`, `documentType`, `required`, `allowedMimeTypes`, `maxSizeMb`, `description`

**DocumentMetadataDTO:** `documentId`, `documentType`, `fileName`, `declaredMimeType`, `declaredSizeBytes`, `documentStatus`, `createdAt` (URL never exposed)

## Swagger UI
`http://localhost:8080/swagger-ui.html` — interactive API documentation with Try-It-Out enabled.
