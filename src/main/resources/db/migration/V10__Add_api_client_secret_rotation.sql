-- ============================================================
-- V10: Extend api_clients for secret rotation (MODULE 2 — Partner Authentication)
-- ============================================================
-- NOTE: the api_clients table itself is created in V2
-- (V2__Create_partner_insurer_mapping.sql). Module 2 only needs to add the
-- column that supports the 24-hour secret rotation grace window.
--
-- This migration must ALTER the existing table, not CREATE it; creating it
-- again would fail on any database that has already applied V2.
-- ============================================================

ALTER TABLE api_clients
    ADD COLUMN IF NOT EXISTS previous_secret_hash VARCHAR(500);

COMMENT ON COLUMN api_clients.previous_secret_hash
    IS 'BCrypt hash of the previous secret, valid only during the 24h rotation grace period.';

-- V2 used the pre-Module 2 scope names ("document:submit", "status:read").
-- Module 2 grants "read", "write" and "admin", and new clients default to
-- read-only, so align the column default with the application contract.
ALTER TABLE api_clients
    ALTER COLUMN scopes SET DEFAULT '["read"]';
