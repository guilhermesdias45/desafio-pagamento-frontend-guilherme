-- V4__create_user_audit_logs.sql
-- Creates the user_audit_logs table for security event tracking.
-- user_id is NULLABLE because failed login attempts with non-existent emails
-- still need to be recorded (no user to associate with).
-- ON DELETE SET NULL preserves audit records when a user is deleted.

CREATE TABLE user_audit_logs (
    id                 UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id            UUID         NULL REFERENCES users(id) ON DELETE SET NULL,
    event_type         VARCHAR(50)  NOT NULL,
    ip_address         VARCHAR(45)  NULL,       -- up to IPv6 length
    device_fingerprint VARCHAR(255) NULL,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- ─── Indexes ─────────────────────────────────────────────────────────────────

CREATE INDEX idx_user_audit_logs_user_id    ON user_audit_logs(user_id);
CREATE INDEX idx_user_audit_logs_created_at ON user_audit_logs(created_at);

-- ─── CHECK constraint for known event types ──────────────────────────────────

ALTER TABLE user_audit_logs ADD CONSTRAINT chk_event_type
    CHECK (event_type IN (
        'LOGIN_SUCCESS',
        'LOGIN_FAILED',
        'ACCOUNT_LOCKED',
        'ACCOUNT_UNLOCKED',
        'REGISTER_SUCCESS',
        '2FA_ENABLED',
        '2FA_DISABLED',
        '2FA_RECOVERY_USED',
        'REFRESH_SUCCESS',
        'REFRESH_FAILED',
        'LOGOUT'
    ));
