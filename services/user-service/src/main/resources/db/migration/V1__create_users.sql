-- V1__create_users.sql
-- Creates user_role and user_status PostgreSQL enums and the users table.
-- merchant_id is nullable WITHOUT FK constraint — the FK is added in V2
-- after the merchants table exists (resolves circular FK dependency).

-- ─── Enums ───────────────────────────────────────────────────────────────────

CREATE TYPE user_role AS ENUM ('CUSTOMER', 'MERCHANT_OWNER', 'STAFF');

CREATE TYPE user_status AS ENUM (
    'PENDING_EMAIL_CONFIRMATION',
    'ACTIVE',
    'LOCKED',
    'DISABLED'
);

-- ─── updated_at trigger function ─────────────────────────────────────────────

CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- ─── users table ─────────────────────────────────────────────────────────────

CREATE TABLE users (
    id                     UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    email                  VARCHAR(255) NOT NULL UNIQUE,
    password_hash          VARCHAR(60)  NOT NULL,                  -- BCrypt fixed 60 chars
    full_name              VARCHAR(100) NOT NULL,
    role                   user_role    NOT NULL,
    merchant_id            UUID         NULL,                      -- FK added in V2
    status                 user_status  NOT NULL DEFAULT 'PENDING_EMAIL_CONFIRMATION',
    totp_enabled           BOOLEAN      NOT NULL DEFAULT false,
    totp_secret_encrypted  TEXT         NULL,
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- ─── Indexes ─────────────────────────────────────────────────────────────────

CREATE INDEX idx_users_email       ON users(email);
CREATE INDEX idx_users_merchant_id ON users(merchant_id) WHERE merchant_id IS NOT NULL;

-- ─── Trigger ─────────────────────────────────────────────────────────────────

CREATE TRIGGER trg_users_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
