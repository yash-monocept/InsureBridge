-- ============================================================
-- V1: Create partners and insurers tables
-- ============================================================

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ============================================================
-- partners
-- External distribution partners (banks, dealers, airlines, etc.)
-- ============================================================
CREATE TABLE partners (
    id                    UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    partner_code          VARCHAR(50) NOT NULL,
    partner_name          VARCHAR(200) NOT NULL,
    partner_type          VARCHAR(50) NOT NULL,
    contact_email         VARCHAR(200) NOT NULL,
    contact_phone         VARCHAR(20),
    website_url           VARCHAR(500),
    description           TEXT,
    is_active             BOOLEAN     NOT NULL DEFAULT TRUE,
    approved_source_hosts TEXT,
    max_document_size_mb  INTEGER     NOT NULL DEFAULT 50,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by            VARCHAR(100),

    CONSTRAINT uq_partners_partner_code UNIQUE (partner_code),
    CONSTRAINT chk_partners_type CHECK (partner_type IN (
        'AUTOMOBILE_DEALER', 'AIRLINE', 'RAILWAY', 'BANK',
        'FINANCIAL_INSTITUTION', 'TRAVEL_PLATFORM', 'ECOMMERCE', 'OTHER'
    )),
    CONSTRAINT chk_partners_max_size CHECK (max_document_size_mb > 0)
);

CREATE INDEX idx_partners_partner_code  ON partners (partner_code);
CREATE INDEX idx_partners_is_active     ON partners (is_active);
CREATE INDEX idx_partners_partner_type  ON partners (partner_type);

COMMENT ON TABLE  partners IS 'External distribution partners that submit insurance requests via this platform.';
COMMENT ON COLUMN partners.partner_code IS 'Short unique business code, e.g. AXIS_BANK, MAX_DEALER.';
COMMENT ON COLUMN partners.approved_source_hosts IS 'JSON array of approved S3 host patterns for SSRF protection.';

-- ============================================================
-- insurers
-- Insurance providers that are the destination of document transfers
-- ============================================================
CREATE TABLE insurers (
    id                          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    insurer_code                VARCHAR(50) NOT NULL,
    insurer_name                VARCHAR(200) NOT NULL,
    insurer_type                VARCHAR(50) NOT NULL,
    contact_email               VARCHAR(200),
    destination_bucket_name     VARCHAR(200),
    destination_bucket_region   VARCHAR(50),
    destination_iam_role_arn    VARCHAR(500),
    destination_path_prefix     VARCHAR(200),
    sns_topic_arn               VARCHAR(500),
    webhook_url                 VARCHAR(500),
    webhook_secret              VARCHAR(500),
    is_active                   BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_insurers_insurer_code UNIQUE (insurer_code),
    CONSTRAINT chk_insurers_type CHECK (insurer_type IN (
        'LIFE', 'HEALTH', 'MOTOR', 'TRAVEL', 'GENERAL'
    ))
);

CREATE INDEX idx_insurers_insurer_code ON insurers (insurer_code);
CREATE INDEX idx_insurers_is_active    ON insurers (is_active);

COMMENT ON TABLE  insurers IS 'Insurance providers that receive document transfers from partners via this platform.';
COMMENT ON COLUMN insurers.destination_iam_role_arn IS 'IAM role ARN for cross-account S3 write access. Never exposed to partners.';
COMMENT ON COLUMN insurers.webhook_secret IS 'HMAC secret for webhook signature verification. Stored encrypted.';
