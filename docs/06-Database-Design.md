# 06 — Database Design (Updated for Modules 3 & 4)

**Status:** COMPLETE (updated)

## Entity Relationship

The Modules 3 & 4 tables were pre-defined by Flyway migrations `V3__Create_schema_engine.sql` and `V4__Create_ingestion.sql` and mapped to JPA entities.

### Module 3 Tables

**ingestion_requests** — master record per partner submission
| Column | Type | Notes |
|--------|------|-------|
| id | UUID PK | |
| external_reference | VARCHAR(200) | partnerReferenceId |
| idempotency_key | VARCHAR(200) UK NOT NULL | duplicate protection |
| partner_id | UUID FK → partners | NOT NULL |
| insurer_id | UUID FK → insurers | NOT NULL |
| product_type | VARCHAR(100) NOT NULL | |
| schema_id | UUID FK → product_schemas | |
| status | VARCHAR(50) NOT NULL | IngestionStatus |
| customer_data | TEXT | serialised JSON |
| product_data | TEXT | serialised JSON |
| metadata | TEXT | |
| validation_result | TEXT | serialised ValidationResultDTO |
| validation_error_count | INTEGER NOT NULL DEFAULT 0 | |
| ip_address | VARCHAR(50) | audit |
| user_agent | VARCHAR(200) | audit |
| received_at | TIMESTAMPTZ NOT NULL | |
| validated_at | TIMESTAMPTZ | |
| created_at, updated_at | TIMESTAMPTZ | audit |

Indexes: `idempotency_key`, `partner_id`, `insurer_id`, `status`, `product_type`, `received_at`

**ingestion_documents** — document references within a submission
| Column | Type | Notes |
|--------|------|-------|
| id | UUID PK | |
| ingestion_request_id | UUID FK → ingestion_requests | NOT NULL |
| document_type | VARCHAR(100) NOT NULL | |
| source_s3_url | VARCHAR(2000) NOT NULL | pre-signed URL (never exposed) |
| source_url_hash | VARCHAR(64) | SHA-256 of URL |
| source_bucket | VARCHAR(200) | parsed from URL |
| source_key | VARCHAR(1000) | parsed from URL |
| original_filename | VARCHAR(500) | |
| declared_mime_type | VARCHAR(100) | |
| declared_size_bytes | BIGINT | |
| document_status | VARCHAR(50) NOT NULL DEFAULT 'PENDING' | DocumentStatus |
| created_at, updated_at | TIMESTAMPTZ | audit |

Indexes: `ingestion_request_id`, `document_type`, `document_status`

### Module 4 Tables

**product_schemas** — from V3
| Column | Type | Notes |
|--------|------|-------|
| id | UUID PK | |
| insurer_id | UUID FK → insurers | NULL = global |
| partner_id | UUID FK → partners | NULL = global |
| product_type | VARCHAR(100) NOT NULL | |
| schema_version | VARCHAR(20) NOT NULL | |
| schema_name | VARCHAR(200) NOT NULL | |
| is_active | BOOLEAN NOT NULL DEFAULT TRUE | |
| created_at, updated_at | TIMESTAMPTZ | |

Unique: `(insurer_id, partner_id, product_type, schema_version)`

**field_rules** — from V3
| Column | Type | Notes |
|--------|------|-------|
| id | UUID PK | |
| schema_id | UUID FK → product_schemas | NOT NULL |
| field_path | VARCHAR(200) NOT NULL | dot notation |
| display_name | VARCHAR(200) NOT NULL | |
| is_required | BOOLEAN DEFAULT TRUE | |
| data_type | VARCHAR(50) NOT NULL | STRING/NUMBER/DATE/BOOLEAN/ENUM |
| min_length, max_length | INTEGER | |
| min_value, max_value | NUMERIC(19,4) | |
| regex_pattern | VARCHAR(500) | |
| allowed_values | TEXT | JSON array |
| date_format | VARCHAR(50) | |
| error_message | VARCHAR(500) | |
| created_at | TIMESTAMPTZ | |

**document_rules** — from V3
| Column | Type | Notes |
|--------|------|-------|
| id | UUID PK | |
| schema_id | UUID FK → product_schemas | NOT NULL |
| document_type | VARCHAR(100) NOT NULL | |
| is_required | BOOLEAN DEFAULT TRUE | |
| allowed_mime_types | TEXT | JSON array |
| max_size_mb | INTEGER DEFAULT 10 | |
| description | VARCHAR(500) | |
| created_at | TIMESTAMPTZ | |

### Pre-existing Tables (unchanged)
`partners`, `insurers`, `partner_insurer_mappings`, `api_clients`, `transfer_jobs`, `destination_documents`, `document_validations`, `webhook_events`, `audit_events`, `outbox_events`.

### JPA Entity Mapping Notes

UUID non-identifier columns use `@JdbcTypeCode(SqlTypes.UUID)` and `@Access(AccessType.FIELD)` for correct Hibernate/H2 binding. JSON-ish columns (customer_data, product_data, metadata, validation_result) are stored as `TEXT` (length 4000) to remain portable across H2 and PostgreSQL.

## Test Isolation

Integration tests run against H2 with `@Transactional` rollback per test; `V1–V10` Flyway migrations are NOT applied in the test profile (`flyway.enabled=false`, `ddl-auto=create-drop`). Entity mappings must be self-consistent against `create-drop`.
