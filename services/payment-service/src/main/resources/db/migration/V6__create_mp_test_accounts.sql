CREATE TABLE mp_test_accounts (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    type               VARCHAR(16) NOT NULL CHECK (type IN ('SELLER', 'BUYER')),
    mp_user_id         BIGINT NOT NULL,
    email              VARCHAR(255) NOT NULL UNIQUE,
    password_enc       TEXT,
    verification_code  VARCHAR(16),
    access_token_enc   TEXT,
    refresh_token_enc  TEXT,
    public_key         VARCHAR(255),
    token_expires_at   TIMESTAMPTZ,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX idx_mp_accounts_seller
  ON mp_test_accounts(type) WHERE type = 'SELLER';
