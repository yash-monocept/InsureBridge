-- ============================================================
-- V5: Transfer tables — transfer_jobs, destination_documents,
--     document_validations
-- ============================================================

-- ============================================================
-- transfer_jobs
-- One transfer job per ingestion request — drives the async pipeline.
-- ============================================================
CREATE TABLE transfer_jobs (
    id                      UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    ingestion_request_id    UUID         NOT NULL,
    sqs_message_id          VARCHAR(200),
    status                  VARCHAR(50)  NOT NULL,
    retry_count             INTEGER      NOT NULL DEFAULT 0,
    max_retries             INTEGER      NOT NULL DEFAULT 3,
    last_error              TEXT,
    last_error_at           TIMESTAMPTZ,
    processing_started_at   TIMESTAMPTZ,
    processing_completed_at TIMESTAMPTZ,
    dead_lettered_at        TIMESTAMPTZ,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_tj_ingestion_request FOREIGN KEY (ingestion_request_id) REFERENCES ingestion_requests (id),
    CONSTRAINT uq_tj_ingestion_request UNIQUE (ingestion_request_id),
    CONSTRAINT chk_tj_retry_count CHECK (retry_count >= 0),
    CONSTRAINT chk_tj_max_retries CHECK (max_retries >= 0)
);

CREATE INDEX idx_tj_ingestion_request_id ON transfer_jobs (ingestion_request_id);
CREATE INDEX idx_tj_status               ON transfer_jobs (status);
CREATE INDEX idx_tj_retry_count          ON transfer_jobs (retry_count);

COMMENT ON TABLE  transfer_jobs IS 'Async processing job for each validated ingestion request. Drives the SQS/Lambda document transfer pipeline.';
COMMENT ON COLUMN transfer_jobs.sqs_message_id IS 'SQS message ID for idempotent duplicate message protection.';

-- ============================================================
-- destination_documents
-- Records each document after successful transfer to insurer S3.
-- ============================================================
CREATE TABLE destination_documents (
    id                          UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    ingestion_document_id       UUID          NOT NULL,
    transfer_job_id             UUID          NOT NULL,
    insurer_id                  UUID          NOT NULL,
    destination_bucket          VARCHAR(200)  NOT NULL,
    destination_key             VARCHAR(1000) NOT NULL,
    destination_url             VARCHAR(2000),
    source_checksum_sha256      VARCHAR(64),
    destination_checksum_sha256 VARCHAR(64),
    checksum_verified           BOOLEAN       NOT NULL DEFAULT FALSE,
    file_size_bytes             BIGINT,
    mime_type                   VARCHAR(100),
    uploaded_at                 TIMESTAMPTZ,
    created_at                  TIMESTAMPTZ   NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_dd_ingestion_document FOREIGN KEY (ingestion_document_id) REFERENCES ingestion_documents (id),
    CONSTRAINT fk_dd_transfer_job       FOREIGN KEY (transfer_job_id)       REFERENCES transfer_jobs (id),
    CONSTRAINT fk_dd_insurer            FOREIGN KEY (insurer_id)            REFERENCES insurers (id)
);

CREATE INDEX idx_dd_ingestion_document_id ON destination_documents (ingestion_document_id);
CREATE INDEX idx_dd_transfer_job_id       ON destination_documents (transfer_job_id);
CREATE INDEX idx_dd_insurer_id            ON destination_documents (insurer_id);
CREATE INDEX idx_dd_destination_bucket    ON destination_documents (destination_bucket);

COMMENT ON TABLE  destination_documents IS 'Final destination record for each document transferred to the insurer S3 bucket.';
COMMENT ON COLUMN destination_documents.destination_key IS 'S3 object key in the insurer bucket — set from trusted server-side configuration.';

-- ============================================================
-- document_validations
-- Detailed document validation results per document.
-- ============================================================
CREATE TABLE document_validations (
    id                    UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    ingestion_document_id UUID         NOT NULL,
    validation_passed     BOOLEAN      NOT NULL,
    actual_mime_type      VARCHAR(100),
    file_signature_valid  BOOLEAN,
    file_size_bytes       BIGINT,
    checksum_sha256       VARCHAR(64),
    validation_errors     JSONB,
    validated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_dv_ingestion_document FOREIGN KEY (ingestion_document_id) REFERENCES ingestion_documents (id)
);

CREATE INDEX idx_dv_ingestion_document_id ON document_validations (ingestion_document_id);
CREATE INDEX idx_dv_validation_passed     ON document_validations (validation_passed);

COMMENT ON TABLE  document_validations IS 'Detailed validation results for each ingested document (MIME type, magic bytes, size, checksum).';
