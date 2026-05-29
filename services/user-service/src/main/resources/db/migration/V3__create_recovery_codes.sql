-- V3__create_recovery_codes.sql
-- Creates the recovery_codes table for 2FA emergency recovery.
-- Each user with 2FA enabled can have up to 8 BCrypt-hashed recovery codes.
-- Deleting a user cascades to delete all associated recovery codes.

CREATE TABLE recovery_codes (
    id         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    code_hash  VARCHAR(60)  NOT NULL,          -- BCrypt hash of the plaintext recovery code
    used       BOOLEAN      NOT NULL DEFAULT false,
    used_at    TIMESTAMPTZ  NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- ─── Index ───────────────────────────────────────────────────────────────────

CREATE INDEX idx_recovery_codes_user_id ON recovery_codes(user_id);
