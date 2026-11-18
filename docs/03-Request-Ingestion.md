# 03 — Request Ingestion (Module 3)

**Status:** COMPLETE  
**Module:** Request Ingestion  
**Owner:** Backend Engineering

## Overview

Module 3 accepts insurance application requests submitted by authenticated external business partners. The request enters the platform through a secured REST API, is validated, persisted, and handed to Module 4's schema validation engine.

## Business Flow

```
Partner API Request
       |
       v
Authentication (JWT from Module 2)
       |
       v
Module 3: Request Ingestion
       |
       v
Persist Submission + Document Metadata
       |
       v
Module 4: Schema Selection + Validation
       |
       v
Validation Result -> Update Submission Status
       |
       v
Return Structured Response
```

## Request Envelope (IngestionRequestDTO)

```json
{
  "partnerReferenceId": "CAR-92831",
  "insurerCode": "MAX_LIFE",
  "productCode": "MOTOR_INSURANCE",
  "schemaVersion": "1.0",
  "customer": {
    "name": "Rahul Sharma",
    "mobile": "9999999999",
    "email": "rahul@example.com"
  },
  "applicationData": {
    "vehicle": {
      "make": "Hyundai",
      "model": "Creta",
      "registrationNumber": "DL01AB1234",
      "manufacturingYear": 2026
    }
  },
  "documents": [
    {
      "documentType": "IDENTITY_PROOF",
      "fileName": "identity.pdf",
      "contentType": "application/pdf",
      "sourceUrl": "https://bucket.s3.amazonaws.com/doc.pdf?signature=..."
    }
  ],
  "idempotencyKey": "idem_CAR-92831_20261116"
}
```

- `applicationData` is a flexible JSON tree supporting motor, travel, health and other insurance categories.
- `idempotencyKey` is optional; if omitted it is derived as `SHA-256(partnerId:partnerReferenceId:productCode:insurerCode)`.

## Authentication & Authorisation

Partner identity is derived **only** from the JWT security context (`PartnerPrincipal`). A `partnerId` supplied in the request body is never trusted as the source of identity.

Checks performed on every submission:
1. Partner exists and is active.
2. Insurer exists and is active.
3. Partner is mapped to the insurer (`PartnerInsurerMapping.active = true`).
4. Product is permitted by the mapping's `permittedProductTypes` (null/empty = all products).
5. Idempotency key uniqueness (database constraint `uq_ir_idempotency_key`).

## Pre-Signed URL Handling (Security)

The partner-supplied `sourceUrl` is sensitive:
- Enforced HTTPS; `http://` rejected.
- Loopback, private, link-local, and cloud-metadata hosts rejected regardless of approved list.
- Host must match one of the partner's `approved_source_hosts` patterns.
- Stored in `ingestion_documents.source_s3_url` (never returned in API responses, never logged in full).
- SHA-256 hash (`source_url_hash`) stored for deduplication without revealing the full URL.

## Idempotency

Duplicate requests are prevented via a database `UNIQUE` constraint on `idempotency_key`. Replaying the same key returns the existing submission (HTTP 409 Conflict) rather than creating a duplicate.

## Request Lifecycle Status

| Status | Meaning |
|--------|---------|
| `RECEIVED` | Request received from partner (initial) |
| `VALIDATING` | Schema validation in progress |
| `VALIDATED` | All validation checks passed |
| `VALIDATION_FAILED` | Validation found blocking errors |

Requests are not marked transferred or completed at Module 3 (that belongs to Modules 6-9).

## Persistence

| Entity | Table | Relationships |
|--------|-------|---------------|
| `IngestionRequest` | `ingestion_requests` | partner (FK), insurer (FK), schema (FK), documents (1:N) |
| `IngestionDocument` | `ingestion_documents` | ingestion request (FK) |

Includes UUIDs, foreign keys, indexes, unique constraints, timestamps, and audit fields. Created by Flyway migration `V4__Create_ingestion.sql` (already present).

## API Endpoints

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/api/v1/partner/insurance/submissions` | JWT `write` | Submit an insurance application |
| GET | `/api/v1/partner/insurance/submissions` | JWT `read` | List partner's submissions (paginated) |
| GET | `/api/v1/partner/insurance/submissions/{id}` | JWT `read` | Get submission detail |
| GET | `/api/v1/partner/insurance/submissions/{id}/validation` | JWT `read` | Get validation result |
| GET | `/api/v1/partner/insurance/submissions/admin/all` | none (academic) | List all submissions (admin) |
| GET | `/api/v1/partner/insurance/submissions/admin/{id}` | none (academic) | Get any submission (admin) |
| GET | `/api/v1/partner/insurance/submissions/admin/{id}/validation` | none (academic) | Get any validation result (admin) |

## Response Structure

Successful submission returns `201 Created` with `IngestionResponseDTO` containing `submissionId`, `externalReference`, `idempotencyKey`, `status`, `validationStatus`, `documentCount`, and sanitised document metadata (URLs excluded).

## Test Coverage

- **Unit tests:** 7 (`IngestionServiceTest`) — valid submission, missing fields, duplicate idempotency, inactive partner, unknown insurer, unmapped insurer, SSRF rejection.
- **Integration tests:** 8 (`IngestionControllerIT`) — full HTTP flow, persistence, partner isolation, idempotency, 401/404/409.
- **Security tests:** 6 (`IngestionSecurityIT`) — missing/invalid/expired tokens, partner isolation, scope enforcement.
