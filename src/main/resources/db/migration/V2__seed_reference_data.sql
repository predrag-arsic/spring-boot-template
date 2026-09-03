-- A second migration, to show the shape of a data change.
--
-- Idempotent (ON CONFLICT DO NOTHING) because a migration may be replayed against a
-- database restored from a backup taken mid-deploy. Seed data belongs in a migration
-- only when the application cannot function without it; demo and test fixtures belong
-- in the tests that need them.

INSERT INTO customers (id, email, display_name, status, version, created_at, updated_at, created_by, updated_by)
VALUES (
    '00000000-0000-0000-0000-000000000001',
    'service-account@vaullet.io',
    'Service Account',
    'ACTIVE',
    0,
    NOW(),
    NOW(),
    'flyway',
    'flyway'
)
ON CONFLICT (id) DO NOTHING;
