-- ============================================================
-- V7: Audit and outbox tables — audit_events, outbox_events
-- ============================================================

-- ============================================================
-- audit_events
-- Immutable audit trail for all significant operations.
-- Rows must never be updated or deleted.
-- ============================================================
CREATE TABLE audit_events (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    entity_type VARCHAR(100) NOT NULL,
    entity_id   VARCHAR(200) NOT NULL,
    action      VARCHAR(100) NOT NULL,
    actor_type  VARCHAR(50)  NOT NULL,
    actor_id    VARCHAR(200),
    old_value   JSONB,
    new_value   JSONB,
    details     TEXT,
    ip_address  VARCHAR(50),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_ae_actor_type CHECK (actor_type IN ('PARTNER', 'ADMIN', 'SYSTEM'))
);

CREATE INDEX idx_ae_entity       ON audit_events (entity_type, entity_id);
CREATE INDEX idx_ae_actor_id     ON audit_events (actor_id);
CREATE INDEX idx_ae_created_at   ON audit_events (created_at);
CREATE INDEX idx_ae_action       ON audit_events (action);

COMMENT ON TABLE  audit_events IS 'Append-only audit trail. Every significant state change is recorded here. Rows are never updated or deleted.';
COMMENT ON COLUMN audit_events.entity_type IS 'e.g. INGESTION_REQUEST, PARTNER, TRANSFER_JOB.';
COMMENT ON COLUMN audit_events.old_value   IS 'Previous entity state as sanitised JSON (no secrets, no full pre-signed URLs).';
COMMENT ON COLUMN audit_events.new_value   IS 'New entity state as sanitised JSON.';

-- ============================================================
-- outbox_events
-- Transactional outbox for reliable SQS message publishing.
-- The outbox poller picks unpublished rows and publishes to SQS.
-- ============================================================
CREATE TABLE outbox_events (
    id                   UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    ingestion_request_id UUID         NOT NULL,
    queue_url            VARCHAR(500) NOT NULL,
    message_body         TEXT         NOT NULL,
    published            BOOLEAN      NOT NULL DEFAULT FALSE,
    published_at         TIMESTAMPTZ,
    publish_attempts     INTEGER      NOT NULL DEFAULT 0,
    last_error           TEXT,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_oe_ingestion_request FOREIGN KEY (ingestion_request_id) REFERENCES ingestion_requests (id),
    CONSTRAINT chk_oe_publish_attempts CHECK (publish_attempts >= 0)
);

-- Composite index on (published, created_at) so the outbox poller
-- can efficiently fetch the oldest unpublished events first.
CREATE INDEX idx_oe_published_created_at ON outbox_events (published, created_at);

COMMENT ON TABLE  outbox_events IS 'Transactional outbox — ensures SQS messages are published reliably within the same DB transaction as the ingestion record.';
COMMENT ON COLUMN outbox_events.published IS 'FALSE until the SQS publish succeeds. Outbox poller sets this to TRUE after successful publication.';
