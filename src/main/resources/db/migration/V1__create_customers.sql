-- Versioned migrations, applied in order and never edited once merged.
--
-- Flyway records a checksum per applied migration, so changing this file after it has
-- run anywhere turns into a validation failure rather than a silent divergence. The fix
-- for a mistake is always a new V<n+1> migration.
--
-- Naming: V<version>__<snake_case_description>.sql (two underscores).

CREATE TABLE customers (
    id           UUID         PRIMARY KEY,
    email        VARCHAR(320) NOT NULL,
    display_name VARCHAR(120) NOT NULL,
    status       VARCHAR(20)  NOT NULL,

    -- Optimistic locking. NOT NULL with a default so rows inserted by a migration or a
    -- direct statement still participate.
    version      BIGINT       NOT NULL DEFAULT 0,

    created_at   TIMESTAMPTZ  NOT NULL,
    updated_at   TIMESTAMPTZ  NOT NULL,
    created_by   VARCHAR(255),
    updated_by   VARCHAR(255),

    -- Enum values are validated in the application; the check constraint stops anything
    -- that reaches the table another way (a script, a support fix) from creating a row
    -- the application cannot read back.
    CONSTRAINT customers_status_check CHECK (status IN ('PENDING', 'ACTIVE', 'SUSPENDED', 'CLOSED'))
);

-- Case-insensitive uniqueness enforced by the database, not only by the service's
-- existsByEmailIgnoreCase check. Two concurrent requests can both pass that check;
-- only the index can actually prevent the duplicate.
CREATE UNIQUE INDEX customers_email_lower_uk ON customers (LOWER(email));

-- Supports the default list ordering (created_at DESC) and the status filter.
CREATE INDEX customers_status_created_at_idx ON customers (status, created_at DESC);
