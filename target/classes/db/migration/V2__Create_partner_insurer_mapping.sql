-- ============================================================
-- V2: Partner-Insurer mappings and API clients
-- ============================================================

-- ============================================================
-- partner_insurer_mappings
-- Maps which insurers a partner is authorised to submit to
-- ============================================================
CREATE TABLE partner_insurer_mappings (
    id                      UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    partner_id              UUID        NOT NULL,
    insurer_id              UUID        NOT NULL,
    is_active               BOOLEAN     NOT NULL DEFAULT TRUE,
    permitted_product_types TEXT,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by              VARCHAR(100),

    CONSTRAINT fk_pim_partner FOREIGN KEY (partner_id) REFERENCES partners (id),
    CONSTRAINT fk_pim_insurer FOREIGN KEY (insurer_id) REFERENCES insurers (id),
    CONSTRAINT uq_pim_partner_insurer UNIQUE (partner_id, insurer_id)
);

CREATE INDEX idx_pim_partner_id ON partner_insurer_mappings (partner_id);
CREATE INDEX idx_pim_insurer_id ON partner_insurer_mappings (insurer_id);
CREATE INDEX idx_pim_is_active  ON partner_insurer_mappings (is_active);

COMMENT ON TABLE  partner_insurer_mappings IS 'Authorisation mapping: which partners may submit to which insurers.';
COMMENT ON COLUMN partner_insurer_mappings.permitted_product_types IS 'JSON array of ProductType values permitted for this partner-insurer pair.';

-- ============================================================
-- api_clients
-- OAuth 2.0 client credentials for partner machine-to-machine auth
-- ============================================================
CREATE TABLE api_clients (
    id                    UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    partner_id            UUID        NOT NULL,
    client_id             VARCHAR(100) NOT NULL,
    client_secret_hash    VARCHAR(500) NOT NULL,
    scopes                TEXT        NOT NULL DEFAULT '["document:submit","status:read"]',
    token_expiry_seconds  INTEGER     NOT NULL DEFAULT 3600,
    rate_limit_per_minute INTEGER     NOT NULL DEFAULT 100,
    is_active             BOOLEAN     NOT NULL DEFAULT TRUE,
    last_rotated_at       TIMESTAMPTZ,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_api_clients_partner FOREIGN KEY (partner_id) REFERENCES partners (id),
    CONSTRAINT uq_api_clients_client_id UNIQUE (client_id),
    CONSTRAINT chk_api_clients_expiry CHECK (token_expiry_seconds > 0),
    CONSTRAINT chk_api_clients_rate_limit CHECK (rate_limit_per_minute > 0)
);

CREATE INDEX idx_api_clients_client_id  ON api_clients (client_id);
CREATE INDEX idx_api_clients_partner_id ON api_clients (partner_id);
CREATE INDEX idx_api_clients_is_active  ON api_clients (is_active);

COMMENT ON TABLE  api_clients IS 'OAuth 2.0 API client credentials for partner authentication. Secrets stored as BCrypt hashes.';
COMMENT ON COLUMN api_clients.client_secret_hash IS 'BCrypt hashed client secret. Plain text never stored.';
COMMENT ON COLUMN api_clients.scopes IS 'JSON array of permitted OAuth scopes.';
