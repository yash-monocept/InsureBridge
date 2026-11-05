-- ============================================================
-- V9: Seed data — global product schemas, field rules, document rules
-- ============================================================
-- Inserts global (insurer_id=NULL, partner_id=NULL) product schemas
-- for MOTOR_INSURANCE and TRAVEL_INSURANCE, along with their
-- field validation rules and required document rules.
-- ON CONFLICT DO NOTHING makes this migration idempotent.
-- ============================================================

-- ============================================================
-- Global product schemas
-- ============================================================
INSERT INTO product_schemas (
    id,
    insurer_id,
    partner_id,
    product_type,
    schema_version,
    schema_name,
    is_active
) VALUES
    (
        '00000000-0000-0000-0001-000000000001',
        NULL,
        NULL,
        'MOTOR_INSURANCE',
        '1.0',
        'Global Motor Insurance Schema v1.0',
        TRUE
    ),
    (
        '00000000-0000-0000-0001-000000000002',
        NULL,
        NULL,
        'TRAVEL_INSURANCE',
        '1.0',
        'Global Travel Insurance Schema v1.0',
        TRUE
    )
ON CONFLICT ON CONSTRAINT uq_ps_insurer_partner_product_version DO NOTHING;

-- ============================================================
-- Field rules — MOTOR_INSURANCE
-- ============================================================
INSERT INTO field_rules (
    schema_id,
    field_path,
    display_name,
    is_required,
    data_type,
    max_length,
    error_message
) VALUES
    (
        '00000000-0000-0000-0001-000000000001',
        'customer.name',
        'Customer Full Name',
        TRUE,
        'STRING',
        200,
        'Customer name is required and must not exceed 200 characters.'
    ),
    (
        '00000000-0000-0000-0001-000000000001',
        'customer.mobile',
        'Customer Mobile Number',
        TRUE,
        'STRING',
        15,
        'Customer mobile number is required.'
    ),
    (
        '00000000-0000-0000-0001-000000000001',
        'vehicle.make',
        'Vehicle Make',
        TRUE,
        'STRING',
        100,
        'Vehicle make (manufacturer) is required.'
    ),
    (
        '00000000-0000-0000-0001-000000000001',
        'vehicle.model',
        'Vehicle Model',
        TRUE,
        'STRING',
        100,
        'Vehicle model is required.'
    ),
    (
        '00000000-0000-0000-0001-000000000001',
        'vehicle.registrationNumber',
        'Vehicle Registration Number',
        TRUE,
        'STRING',
        20,
        'Vehicle registration number is required.'
    )
ON CONFLICT DO NOTHING;

-- ============================================================
-- Field rules — TRAVEL_INSURANCE
-- ============================================================
INSERT INTO field_rules (
    schema_id,
    field_path,
    display_name,
    is_required,
    data_type,
    max_length,
    error_message
) VALUES
    (
        '00000000-0000-0000-0001-000000000002',
        'customer.name',
        'Customer Full Name',
        TRUE,
        'STRING',
        200,
        'Customer name is required and must not exceed 200 characters.'
    ),
    (
        '00000000-0000-0000-0001-000000000002',
        'customer.mobile',
        'Customer Mobile Number',
        TRUE,
        'STRING',
        15,
        'Customer mobile number is required.'
    ),
    (
        '00000000-0000-0000-0001-000000000002',
        'passenger.age',
        'Passenger Age',
        TRUE,
        'NUMBER',
        NULL,
        'Passenger age is required and must be a positive number.'
    ),
    (
        '00000000-0000-0000-0001-000000000002',
        'journey.departureDate',
        'Journey Departure Date',
        TRUE,
        'DATE',
        NULL,
        'Journey departure date is required in yyyy-MM-dd format.'
    ),
    (
        '00000000-0000-0000-0001-000000000002',
        'journey.destination',
        'Journey Destination',
        TRUE,
        'STRING',
        200,
        'Journey destination is required.'
    )
ON CONFLICT DO NOTHING;

-- ============================================================
-- Document rules — MOTOR_INSURANCE
-- ============================================================
INSERT INTO document_rules (
    schema_id,
    document_type,
    is_required,
    allowed_mime_types,
    max_size_mb,
    description
) VALUES
    (
        '00000000-0000-0000-0001-000000000001',
        'IDENTITY_PROOF',
        TRUE,
        '["application/pdf","image/jpeg","image/jpg","image/png"]',
        10,
        'Government-issued identity proof (Aadhaar, PAN, Passport, Driving Licence).'
    ),
    (
        '00000000-0000-0000-0001-000000000001',
        'VEHICLE_DOCUMENT',
        TRUE,
        '["application/pdf","image/jpeg","image/jpg","image/png"]',
        10,
        'Vehicle registration certificate (RC book) or equivalent document.'
    )
ON CONFLICT DO NOTHING;

-- ============================================================
-- Document rules — TRAVEL_INSURANCE
-- ============================================================
INSERT INTO document_rules (
    schema_id,
    document_type,
    is_required,
    allowed_mime_types,
    max_size_mb,
    description
) VALUES
    (
        '00000000-0000-0000-0001-000000000002',
        'IDENTITY_PROOF',
        TRUE,
        '["application/pdf","image/jpeg","image/jpg","image/png"]',
        10,
        'Government-issued identity proof (Aadhaar, PAN, Passport).'
    ),
    (
        '00000000-0000-0000-0001-000000000002',
        'BOOKING_CONFIRMATION',
        TRUE,
        '["application/pdf","image/jpeg","image/jpg","image/png"]',
        10,
        'Flight, train, or hotel booking confirmation document.'
    )
ON CONFLICT DO NOTHING;
