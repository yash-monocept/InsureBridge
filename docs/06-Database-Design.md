# 06 — Database Design

## A. Design Principles

1. PostgreSQL with UUID primary keys for integration records
2. Foreign keys enforced at database level
3. Unique constraints on business identifiers
4. Indexed on foreign keys and frequently queried columns
5. No document binary content stored in PostgreSQL
6. Flyway versioned migrations
7. Soft-delete pattern (active/status flags) — no hard deletes on business records
8. Audit timestamps on every entity (created_at, updated_at)

---

## B. Entity Catalogue

### B.1 partners

Represents an external distribution partner (car dealer, bank, airline, etc.)

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | UUID | PK | Internal identifier |
| partner_code | VARCHAR(50) | UNIQUE NOT NULL | Short business code, e.g. AXIS_BANK |
| partner_name | VARCHAR(200) | NOT NULL | Full partner name |
| partner_type | VARCHAR(50) | NOT NULL | AUTOMOBILE_DEALER, AIRLINE, RAILWAY, BANK, TRAVEL_PLATFORM, ECOMMERCE, FINANCIAL_INSTITUTION |
| contact_email | VARCHAR(200) | NOT NULL | Primary contact email |
| contact_phone | VARCHAR(20) | | Contact phone |
| website_url | VARCHAR(500) | | Partner website |
| description | TEXT | | Description |
| is_active | BOOLEAN | NOT NULL DEFAULT TRUE | Active status |
| approved_source_hosts | TEXT | | JSON array of approved S3 host patterns |
| max_document_size_mb | INTEGER | DEFAULT 50 | Max file size in MB |
| created_at | TIMESTAMP WITH TIME ZONE | NOT NULL | Creation timestamp |
| updated_at | TIMESTAMP WITH TIME ZONE | NOT NULL | Last update timestamp |
| created_by | VARCHAR(100) | | Admin who created |

**Indexes:** partner_code, is_active, partner_type

---

### B.2 insurers

Represents an insurance provider (Max Life, Care Health, etc.)

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | UUID | PK | Internal identifier |
| insurer_code | VARCHAR(50) | UNIQUE NOT NULL | Short code, e.g. MAX_LIFE |
| insurer_name | VARCHAR(200) | NOT NULL | Full name |
| insurer_type | VARCHAR(50) | NOT NULL | LIFE, HEALTH, MOTOR, TRAVEL, GENERAL |
| contact_email | VARCHAR(200) | | Contact email |
| destination_bucket_name | VARCHAR(200) | | Insurer's S3 bucket name |
| destination_bucket_region | VARCHAR(50) | | AWS region |
| destination_iam_role_arn | VARCHAR(500) | | IAM role ARN for cross-account access |
| destination_path_prefix | VARCHAR(200) | | S3 key prefix for uploaded docs |
| sns_topic_arn | VARCHAR(500) | | SNS topic to notify |
| webhook_url | VARCHAR(500) | | Webhook notification URL |
| webhook_secret | VARCHAR(500) | | Webhook HMAC secret (encrypted) |
| is_active | BOOLEAN | NOT NULL DEFAULT TRUE | Active status |
| created_at | TIMESTAMP WITH TIME ZONE | NOT NULL | Creation timestamp |
| updated_at | TIMESTAMP WITH TIME ZONE | NOT NULL | Last update timestamp |

**Indexes:** insurer_code, is_active

---

### B.3 partner_insurer_mappings

Maps which insurers a partner is authorised to submit to.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | UUID | PK | |
| partner_id | UUID | FK → partners.id NOT NULL | |
| insurer_id | UUID | FK → insurers.id NOT NULL | |
| is_active | BOOLEAN | NOT NULL DEFAULT TRUE | |
| permitted_product_types | TEXT | | JSON array of permitted product types |
| created_at | TIMESTAMP WITH TIME ZONE | NOT NULL | |
| created_by | VARCHAR(100) | | |

**Unique Constraint:** (partner_id, insurer_id)
**Indexes:** partner_id, insurer_id

---

### B.4 api_clients

OAuth 2.0 API client credentials for partners.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | UUID | PK | |
| partner_id | UUID | FK → partners.id NOT NULL | |
| client_id | VARCHAR(100) | UNIQUE NOT NULL | Public client identifier |
| client_secret_hash | VARCHAR(500) | NOT NULL | BCrypt hashed secret |
| scopes | TEXT | NOT NULL | JSON array of permitted scopes |
| token_expiry_seconds | INTEGER | DEFAULT 3600 | Token validity |
| rate_limit_per_minute | INTEGER | DEFAULT 100 | Rate limit |
| is_active | BOOLEAN | NOT NULL DEFAULT TRUE | |
| last_rotated_at | TIMESTAMP WITH TIME ZONE | | Last secret rotation |
| created_at | TIMESTAMP WITH TIME ZONE | NOT NULL | |
| updated_at | TIMESTAMP WITH TIME ZONE | NOT NULL | |

**Indexes:** client_id, partner_id, is_active

---

### B.5 product_schemas

Defines the validation schema for a product type (MOTOR_INSURANCE, TRAVEL_INSURANCE, etc.)

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | UUID | PK | |
| insurer_id | UUID | FK → insurers.id | If null = global schema |
| partner_id | UUID | FK → partners.id | If null = global schema |
| product_type | VARCHAR(100) | NOT NULL | MOTOR, TRAVEL, HEALTH, LIFE, etc. |
| schema_version | VARCHAR(20) | NOT NULL | e.g. "1.0", "2.1" |
| schema_name | VARCHAR(200) | NOT NULL | Human name |
| is_active | BOOLEAN | NOT NULL DEFAULT TRUE | |
| created_at | TIMESTAMP WITH TIME ZONE | NOT NULL | |
| updated_at | TIMESTAMP WITH TIME ZONE | NOT NULL | |

**Unique Constraint:** (insurer_id, partner_id, product_type, schema_version)
**Indexes:** product_type, insurer_id, is_active

---

### B.6 field_rules

Defines validation rules for individual fields within a product schema.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | UUID | PK | |
| schema_id | UUID | FK → product_schemas.id NOT NULL | |
| field_path | VARCHAR(200) | NOT NULL | Dot-notation path e.g. customer.name |
| display_name | VARCHAR(200) | NOT NULL | Human-readable name |
| is_required | BOOLEAN | NOT NULL DEFAULT TRUE | |
| data_type | VARCHAR(50) | NOT NULL | STRING, NUMBER, DATE, BOOLEAN, ENUM |
| min_length | INTEGER | | |
| max_length | INTEGER | | |
| min_value | NUMERIC | | |
| max_value | NUMERIC | | |
| regex_pattern | VARCHAR(500) | | Validation regex |
| allowed_values | TEXT | | JSON array for ENUM type |
| date_format | VARCHAR(50) | | e.g. yyyy-MM-dd |
| error_message | VARCHAR(500) | | Custom error message |
| created_at | TIMESTAMP WITH TIME ZONE | NOT NULL | |

**Indexes:** schema_id, field_path

---

### B.7 document_rules

Defines required document types per product schema.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | UUID | PK | |
| schema_id | UUID | FK → product_schemas.id NOT NULL | |
| document_type | VARCHAR(100) | NOT NULL | IDENTITY_PROOF, VEHICLE_DOCUMENT, etc. |
| is_required | BOOLEAN | NOT NULL DEFAULT TRUE | |
| allowed_mime_types | TEXT | | JSON array |
| max_size_mb | INTEGER | DEFAULT 10 | |
| description | VARCHAR(500) | | |
| created_at | TIMESTAMP WITH TIME ZONE | NOT NULL | |

**Indexes:** schema_id, document_type

---

### B.8 ingestion_requests

The main request record — one per partner API call.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | UUID | PK | |
| external_reference | VARCHAR(200) | | Partner's own reference ID |
| idempotency_key | VARCHAR(200) | UNIQUE NOT NULL | Prevents duplicate submissions |
| partner_id | UUID | FK → partners.id NOT NULL | |
| insurer_id | UUID | FK → insurers.id NOT NULL | |
| product_type | VARCHAR(100) | NOT NULL | |
| schema_id | UUID | FK → product_schemas.id | Schema used for validation |
| status | VARCHAR(50) | NOT NULL | Processing status enum |
| customer_data | JSONB | NOT NULL | Customer information |
| product_data | JSONB | NOT NULL | Insurance product/vehicle/travel data |
| metadata | JSONB | | Additional metadata |
| validation_result | JSONB | | Structured validation output |
| validation_error_count | INTEGER | DEFAULT 0 | |
| ip_address | VARCHAR(50) | | Submitting IP |
| user_agent | VARCHAR(200) | | |
| received_at | TIMESTAMP WITH TIME ZONE | NOT NULL | |
| validated_at | TIMESTAMP WITH TIME ZONE | | |
| queued_at | TIMESTAMP WITH TIME ZONE | | |
| completed_at | TIMESTAMP WITH TIME ZONE | | |
| created_at | TIMESTAMP WITH TIME ZONE | NOT NULL | |
| updated_at | TIMESTAMP WITH TIME ZONE | NOT NULL | |

**Indexes:** idempotency_key, partner_id, insurer_id, status, product_type, received_at

---

### B.9 ingestion_documents

Individual document references within an ingestion request.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | UUID | PK | |
| ingestion_request_id | UUID | FK → ingestion_requests.id NOT NULL | |
| document_type | VARCHAR(100) | NOT NULL | |
| source_s3_url | VARCHAR(2000) | NOT NULL | Pre-signed URL (DO NOT LOG FULL) |
| source_url_hash | VARCHAR(64) | | SHA-256 of URL for deduplication |
| source_bucket | VARCHAR(200) | | Extracted bucket name |
| source_key | VARCHAR(1000) | | Extracted S3 key |
| original_filename | VARCHAR(500) | | |
| declared_mime_type | VARCHAR(100) | | Declared by partner |
| declared_size_bytes | BIGINT | | Declared by partner |
| document_status | VARCHAR(50) | NOT NULL DEFAULT 'PENDING' | |
| created_at | TIMESTAMP WITH TIME ZONE | NOT NULL | |
| updated_at | TIMESTAMP WITH TIME ZONE | NOT NULL | |

**Indexes:** ingestion_request_id, document_type, document_status

---

### B.10 transfer_jobs

One transfer job per ingestion request — drives the async pipeline.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | UUID | PK | |
| ingestion_request_id | UUID | FK → ingestion_requests.id UNIQUE NOT NULL | |
| sqs_message_id | VARCHAR(200) | | SQS message ID |
| status | VARCHAR(50) | NOT NULL | Transfer job status |
| retry_count | INTEGER | NOT NULL DEFAULT 0 | |
| max_retries | INTEGER | NOT NULL DEFAULT 3 | |
| last_error | TEXT | | Last failure message |
| last_error_at | TIMESTAMP WITH TIME ZONE | | |
| processing_started_at | TIMESTAMP WITH TIME ZONE | | |
| processing_completed_at | TIMESTAMP WITH TIME ZONE | | |
| dead_lettered_at | TIMESTAMP WITH TIME ZONE | | |
| created_at | TIMESTAMP WITH TIME ZONE | NOT NULL | |
| updated_at | TIMESTAMP WITH TIME ZONE | NOT NULL | |

**Indexes:** ingestion_request_id, status, retry_count

---

### B.11 destination_documents

Records each document after successful transfer to insurer S3.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | UUID | PK | |
| ingestion_document_id | UUID | FK → ingestion_documents.id NOT NULL | |
| transfer_job_id | UUID | FK → transfer_jobs.id NOT NULL | |
| insurer_id | UUID | FK → insurers.id NOT NULL | |
| destination_bucket | VARCHAR(200) | NOT NULL | |
| destination_key | VARCHAR(1000) | NOT NULL | |
| destination_url | VARCHAR(2000) | | S3 URI |
| source_checksum_sha256 | VARCHAR(64) | | Checksum at source |
| destination_checksum_sha256 | VARCHAR(64) | | Checksum at destination |
| checksum_verified | BOOLEAN | DEFAULT FALSE | |
| file_size_bytes | BIGINT | | |
| mime_type | VARCHAR(100) | | |
| uploaded_at | TIMESTAMP WITH TIME ZONE | | |
| created_at | TIMESTAMP WITH TIME ZONE | NOT NULL | |

**Indexes:** ingestion_document_id, transfer_job_id, insurer_id, destination_bucket

---

### B.12 document_validations

Detailed document validation results per document.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | UUID | PK | |
| ingestion_document_id | UUID | FK → ingestion_documents.id NOT NULL | |
| validation_passed | BOOLEAN | NOT NULL | |
| actual_mime_type | VARCHAR(100) | | |
| file_signature_valid | BOOLEAN | | Magic bytes check |
| file_size_bytes | BIGINT | | |
| checksum_sha256 | VARCHAR(64) | | |
| validation_errors | JSONB | | Array of error messages |
| validated_at | TIMESTAMP WITH TIME ZONE | NOT NULL | |

**Indexes:** ingestion_document_id, validation_passed

---

### B.13 webhook_events

Notification delivery records.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | UUID | PK | |
| ingestion_request_id | UUID | FK → ingestion_requests.id NOT NULL | |
| event_type | VARCHAR(100) | NOT NULL | e.g. DOCUMENT_TRANSFER_COMPLETED |
| recipient_type | VARCHAR(50) | NOT NULL | PARTNER, INSURER |
| recipient_id | UUID | NOT NULL | Partner ID or Insurer ID |
| delivery_method | VARCHAR(50) | NOT NULL | WEBHOOK, SNS |
| payload | JSONB | NOT NULL | Event payload |
| status | VARCHAR(50) | NOT NULL | PENDING, DELIVERED, FAILED |
| http_status_code | INTEGER | | For webhook delivery |
| response_body | TEXT | | For webhook delivery |
| retry_count | INTEGER | DEFAULT 0 | |
| last_attempted_at | TIMESTAMP WITH TIME ZONE | | |
| delivered_at | TIMESTAMP WITH TIME ZONE | | |
| created_at | TIMESTAMP WITH TIME ZONE | NOT NULL | |

**Indexes:** ingestion_request_id, status, event_type, recipient_id

---

### B.14 audit_events

Immutable audit trail for all significant operations.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | UUID | PK | |
| entity_type | VARCHAR(100) | NOT NULL | e.g. INGESTION_REQUEST, PARTNER |
| entity_id | VARCHAR(200) | NOT NULL | UUID of the entity |
| action | VARCHAR(100) | NOT NULL | e.g. CREATED, STATUS_CHANGED |
| actor_type | VARCHAR(50) | NOT NULL | PARTNER, ADMIN, SYSTEM |
| actor_id | VARCHAR(200) | | ID of actor |
| old_value | JSONB | | Previous state (sanitized) |
| new_value | JSONB | | New state (sanitized) |
| details | TEXT | | Human-readable description |
| ip_address | VARCHAR(50) | | |
| created_at | TIMESTAMP WITH TIME ZONE | NOT NULL | |

**Indexes:** entity_type + entity_id, actor_id, created_at, action

---

### B.15 outbox_events

Transactional outbox for reliable SQS publishing.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | UUID | PK | |
| ingestion_request_id | UUID | FK → ingestion_requests.id NOT NULL | |
| queue_url | VARCHAR(500) | NOT NULL | Target SQS queue URL |
| message_body | TEXT | NOT NULL | JSON payload |
| published | BOOLEAN | NOT NULL DEFAULT FALSE | |
| published_at | TIMESTAMP WITH TIME ZONE | | |
| publish_attempts | INTEGER | NOT NULL DEFAULT 0 | |
| last_error | TEXT | | |
| created_at | TIMESTAMP WITH TIME ZONE | NOT NULL | |

**Indexes:** published, created_at (for outbox poller)

---

## C. ER Relationships Summary

```
partners ──< api_clients          (1 partner → N API clients)
partners ──< partner_insurer_mappings >── insurers
insurers ──< product_schemas
product_schemas ──< field_rules
product_schemas ──< document_rules
partners ──< ingestion_requests >── insurers
ingestion_requests ──< ingestion_documents
ingestion_requests ──1── transfer_jobs
ingestion_documents ──< destination_documents
ingestion_documents ──1── document_validations
transfer_jobs ──< destination_documents
ingestion_requests ──< webhook_events
ingestion_requests ──< audit_events
ingestion_requests ──1── outbox_events
```

---

## D. Flyway Migration Plan

| Version | File | Contents |
|---------|------|---------|
| V1 | V1__Create_partners_insurers.sql | partners, insurers tables |
| V2 | V2__Create_partner_insurer_mapping.sql | partner_insurer_mappings |
| V3 | V3__Create_api_clients.sql | api_clients |
| V4 | V4__Create_schema_engine.sql | product_schemas, field_rules, document_rules |
| V5 | V5__Create_ingestion.sql | ingestion_requests, ingestion_documents |
| V6 | V6__Create_transfer.sql | transfer_jobs, destination_documents, document_validations |
| V7 | V7__Create_notifications.sql | webhook_events |
| V8 | V8__Create_audit_outbox.sql | audit_events, outbox_events |
| V9 | V9__Seed_insurers.sql | Seed Max Life, Axis Max Life, Care Health |
| V10 | V10__Seed_product_schemas.sql | Motor, Travel, Health base schemas |
