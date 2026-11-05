-- ============================================================
-- V4: Ingestion tables — ingestion_requests, ingestion_documents
-- ============================================================

-- ============================================================
-- ingestion_requests
-- One record per partner API submission call.
-- ============================================================
CREATE TABLE ingestion_requests (
    id                      UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    external_reference      VARCHAR(200),
    idempotency_key         VARCHAR(200) NOT NULL,
    partner_id              UUID         NOT NULL,
    insurer_id              UUID         NOT NULL,
    product_type            VARCHAR(100) NOT NULL,
    schema_id               UUID,
    status                  VARCHAR(50)  NOT NULL,
    customer_data           JSONB        NOT NULL,
    product_data            JSONB        NOT NULL,
    metadata                JSONB,
    validation_result       JSONB,
    validation_error_count  INTEGER      NOT NULL DEFAULT 0,
    ip_address              VARCHAR(50),
    user_agent              VARCHAR(200),
    received_at             TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    validated_at            TIMESTAMPTZ,
    queued_at               TIMESTAMPTZ,
    completed_at            TIMESTAMPTZ,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_ir_partner   FOREIGN KEY (partner_id) REFERENCES partners (id),
    CONSTRAINT fk_ir_insurer   FOREIGN KEY (insurer_id) REFERENCES insurers (id),
    CONSTRAINT fk_ir_schema    FOREIGN KEY (schema_id)  REFERENCES product_schemas (id),
    CONSTRAINT uq_ir_idempotency_key UNIQUE (idempotency_key)
);

CREATE INDEX idx_ir_idempotency_key ON ingestion_requests (idempotency_key);
CREATE INDEX idx_ir_partner_id      ON ingestion_requests (partner_id);
CREATE INDEX idx_ir_insurer_id      ON ingestion_requests (insurer_id);
CREATE INDEX idx_ir_status          ON ingestion_requests (status);
CREATE INDEX idx_ir_product_type    ON ingestion_requests (product_type);
CREATE INDEX idx_ir_received_at     ON ingestion_requests (received_at);

COMMENT ON TABLE  ingestion_requests IS 'Master record for each partner insurance submission. Drives the entire processing lifecycle.';
COMMENT ON COLUMN ingestion_requests.idempotency_key  IS 'Partner-supplied key to prevent duplicate submissions.';
COMMENT ON COLUMN ingestion_requests.customer_data    IS 'JSONB customer information submitted by the partner.';
COMMENT ON COLUMN ingestion_requests.product_data     IS 'JSONB product-specific data (vehicle, travel, etc.).';
COMMENT ON COLUMN ingestion_requests.validation_result IS 'Structured validation output as JSONB.';

-- ============================================================
-- ingestion_documents
-- Individual document references within an ingestion request.
-- ============================================================
CREATE TABLE ingestion_documents (
    id                   UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    ingestion_request_id UUID          NOT NULL,
    document_type        VARCHAR(100)  NOT NULL,
    source_s3_url        VARCHAR(2000) NOT NULL,
    source_url_hash      VARCHAR(64),
    source_bucket        VARCHAR(200),
    source_key           VARCHAR(1000),
    original_filename    VARCHAR(500),
    declared_mime_type   VARCHAR(100),
    declared_size_bytes  BIGINT,
    document_status      VARCHAR(50)   NOT NULL DEFAULT 'PENDING',
    created_at           TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ   NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_id_ingestion_request FOREIGN KEY (ingestion_request_id) REFERENCES ingestion_requests (id)
);

CREATE INDEX idx_id_ingestion_request_id ON ingestion_documents (ingestion_request_id);
CREATE INDEX idx_id_document_type        ON ingestion_documents (document_type);
CREATE INDEX idx_id_document_status      ON ingestion_documents (document_status);

COMMENT ON TABLE  ingestion_documents IS 'Individual document references submitted within an ingestion request.';
COMMENT ON COLUMN ingestion_documents.source_s3_url   IS 'Pre-signed S3 URL supplied by partner. DO NOT LOG IN FULL.';
COMMENT ON COLUMN ingestion_documents.source_url_hash IS 'SHA-256 hash of the pre-signed URL for deduplication without exposing the full URL.';
