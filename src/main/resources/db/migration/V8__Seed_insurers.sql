-- ============================================================
-- V8: Seed data — base insurers
-- ============================================================
-- Inserts the three primary insurance providers used by the platform.
-- ON CONFLICT DO NOTHING makes this migration idempotent.
-- AWS bucket/region/ARN values are placeholder strings that must be
-- replaced with real values via environment-specific configuration
-- before any production use.
-- ============================================================

INSERT INTO insurers (
    insurer_code,
    insurer_name,
    insurer_type,
    contact_email,
    destination_bucket_name,
    destination_bucket_region,
    destination_iam_role_arn,
    destination_path_prefix,
    is_active
) VALUES
    (
        'MAX_LIFE',
        'Max Life Insurance Company Limited',
        'LIFE',
        'integrations@maxlife.in',
        'max-life-documents-placeholder',
        'ap-south-1',
        'arn:aws:iam::PLACEHOLDER_MAX_LIFE_ACCOUNT:role/InsuranceIntegrationRole',
        'inbound/middleware/',
        TRUE
    ),
    (
        'AXIS_MAX_LIFE',
        'Axis Max Life Insurance Company Limited',
        'LIFE',
        'integrations@axismaxlife.in',
        'axis-max-life-documents-placeholder',
        'ap-south-1',
        'arn:aws:iam::PLACEHOLDER_AXIS_MAX_LIFE_ACCOUNT:role/InsuranceIntegrationRole',
        'inbound/middleware/',
        TRUE
    ),
    (
        'CARE_HEALTH',
        'Care Health Insurance Limited',
        'HEALTH',
        'integrations@carehealth.in',
        'care-health-documents-placeholder',
        'ap-south-1',
        'arn:aws:iam::PLACEHOLDER_CARE_HEALTH_ACCOUNT:role/InsuranceIntegrationRole',
        'inbound/middleware/',
        TRUE
    )
ON CONFLICT (insurer_code) DO NOTHING;
