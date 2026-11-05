-- ============================================================
-- V3: Schema engine — product_schemas, field_rules, document_rules
-- ============================================================
-- NOTE: api_clients was already created in V2.
-- This migration creates only the schema validation engine tables.
-- ============================================================

-- ============================================================
-- product_schemas
-- Defines the validation schema for a product type.
-- insurer_id=NULL and partner_id=NULL means global schema.
-- ============================================================
CREATE TABLE product_schemas (
    id             UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    insurer_id     UUID,
    partner_id     UUID,
    product_type   VARCHAR(100) NOT NULL,
    schema_version VARCHAR(20)  NOT NULL,
    schema_name    VARCHAR(200) NOT NULL,
    is_active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_ps_insurer FOREIGN KEY (insurer_id) REFERENCES insurers (id),
    CONSTRAINT fk_ps_partner FOREIGN KEY (partner_id) REFERENCES partners (id),
    CONSTRAINT uq_ps_insurer_partner_product_version
        UNIQUE (insurer_id, partner_id, product_type, schema_version)
);

CREATE INDEX idx_ps_product_type ON product_schemas (product_type);
CREATE INDEX idx_ps_insurer_id   ON product_schemas (insurer_id);
CREATE INDEX idx_ps_is_active    ON product_schemas (is_active);

COMMENT ON TABLE  product_schemas IS 'Configurable validation schemas for each insurance product type. NULL insurer/partner means global default schema.';
COMMENT ON COLUMN product_schemas.insurer_id IS 'NULL = applies to all insurers (global schema).';
COMMENT ON COLUMN product_schemas.partner_id IS 'NULL = applies to all partners (global schema).';

-- ============================================================
-- field_rules
-- Validation rules for individual fields within a product schema.
-- ============================================================
CREATE TABLE field_rules (
    id             UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    schema_id      UUID         NOT NULL,
    field_path     VARCHAR(200) NOT NULL,
    display_name   VARCHAR(200) NOT NULL,
    is_required    BOOLEAN      NOT NULL DEFAULT TRUE,
    data_type      VARCHAR(50)  NOT NULL,
    min_length     INTEGER,
    max_length     INTEGER,
    min_value      NUMERIC,
    max_value      NUMERIC,
    regex_pattern  VARCHAR(500),
    allowed_values TEXT,
    date_format    VARCHAR(50),
    error_message  VARCHAR(500),
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_fr_schema FOREIGN KEY (schema_id) REFERENCES product_schemas (id),
    CONSTRAINT chk_fr_data_type CHECK (data_type IN (
        'STRING', 'NUMBER', 'DATE', 'BOOLEAN', 'ENUM'
    ))
);

CREATE INDEX idx_fr_schema_id  ON field_rules (schema_id);
CREATE INDEX idx_fr_field_path ON field_rules (field_path);

COMMENT ON TABLE  field_rules IS 'Per-field validation rules for a product schema.';
COMMENT ON COLUMN field_rules.field_path     IS 'Dot-notation path into the request JSON, e.g. customer.name or vehicle.registrationNumber.';
COMMENT ON COLUMN field_rules.allowed_values IS 'JSON array of permitted string values for ENUM type fields.';

-- ============================================================
-- document_rules
-- Defines required document types per product schema.
-- ============================================================
CREATE TABLE document_rules (
    id                UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    schema_id         UUID         NOT NULL,
    document_type     VARCHAR(100) NOT NULL,
    is_required       BOOLEAN      NOT NULL DEFAULT TRUE,
    allowed_mime_types TEXT,
    max_size_mb       INTEGER      NOT NULL DEFAULT 10,
    description       VARCHAR(500),
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_dr_schema FOREIGN KEY (schema_id) REFERENCES product_schemas (id)
);

CREATE INDEX idx_dr_schema_id     ON document_rules (schema_id);
CREATE INDEX idx_dr_document_type ON document_rules (document_type);

COMMENT ON TABLE  document_rules IS 'Required document types per product schema.';
COMMENT ON COLUMN document_rules.allowed_mime_types IS 'JSON array of accepted MIME types, e.g. ["application/pdf","image/jpeg"].';
