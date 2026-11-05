-- ============================================================
-- V6: Notification tables — webhook_events
-- ============================================================

-- ============================================================
-- webhook_events
-- Notification delivery records for partner and insurer callbacks.
-- ============================================================
CREATE TABLE webhook_events (
    id                   UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    ingestion_request_id UUID         NOT NULL,
    event_type           VARCHAR(100) NOT NULL,
    recipient_type       VARCHAR(50)  NOT NULL,
    recipient_id         UUID         NOT NULL,
    delivery_method      VARCHAR(50)  NOT NULL,
    payload              JSONB        NOT NULL,
    status               VARCHAR(50)  NOT NULL,
    http_status_code     INTEGER,
    response_body        TEXT,
    retry_count          INTEGER      NOT NULL DEFAULT 0,
    last_attempted_at    TIMESTAMPTZ,
    delivered_at         TIMESTAMPTZ,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_we_ingestion_request FOREIGN KEY (ingestion_request_id) REFERENCES ingestion_requests (id),
    CONSTRAINT chk_we_recipient_type CHECK (recipient_type IN ('PARTNER', 'INSURER')),
    CONSTRAINT chk_we_delivery_method CHECK (delivery_method IN ('WEBHOOK', 'SNS')),
    CONSTRAINT chk_we_status CHECK (status IN ('PENDING', 'DELIVERED', 'FAILED', 'SKIPPED')),
    CONSTRAINT chk_we_retry_count CHECK (retry_count >= 0)
);

CREATE INDEX idx_we_ingestion_request_id ON webhook_events (ingestion_request_id);
CREATE INDEX idx_we_status               ON webhook_events (status);
CREATE INDEX idx_we_event_type           ON webhook_events (event_type);
CREATE INDEX idx_we_recipient_id         ON webhook_events (recipient_id);

COMMENT ON TABLE  webhook_events IS 'Notification delivery log for all partner and insurer webhook/SNS events.';
COMMENT ON COLUMN webhook_events.event_type IS 'e.g. INGESTION_RECEIVED, VALIDATION_FAILED, DOCUMENT_TRANSFER_COMPLETED.';
COMMENT ON COLUMN webhook_events.payload    IS 'JSON event payload sent to the recipient. Must not contain secrets.';
