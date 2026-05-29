-- V2__create_merchants.sql
-- Creates merchant_status enum, merchants table with FK owner_id → users(id),
-- and ADDS the circular FK users.merchant_id → merchants(id) at the end of this
-- single atomic migration (resolves the circular dependency left open in V1).

-- ─── Enum ────────────────────────────────────────────────────────────────────

CREATE TYPE merchant_status AS ENUM ('ACTIVE', 'SUSPENDED', 'INACTIVE');

-- ─── merchants table ─────────────────────────────────────────────────────────

CREATE TABLE merchants (
    id           UUID              PRIMARY KEY DEFAULT gen_random_uuid(),
    company_name VARCHAR(100)      NOT NULL,
    cnpj         VARCHAR(14)       NOT NULL UNIQUE,
    owner_id     UUID              NOT NULL REFERENCES users(id),
    status       merchant_status   NOT NULL DEFAULT 'ACTIVE',
    created_at   TIMESTAMPTZ       NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ       NOT NULL DEFAULT NOW()
);

-- ─── Indexes ─────────────────────────────────────────────────────────────────

CREATE INDEX idx_merchants_cnpj ON merchants(cnpj);

-- ─── Trigger ─────────────────────────────────────────────────────────────────

CREATE TRIGGER trg_merchants_updated_at
    BEFORE UPDATE ON merchants
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ─── Close the circular FK: users.merchant_id → merchants(id) ───────────────
-- V1 left merchant_id as UUID NULLABLE without FK to avoid a chicken-and-egg problem.
-- Now that merchants exists, we add the FK constraint.

ALTER TABLE users
    ADD CONSTRAINT fk_users_merchant
    FOREIGN KEY (merchant_id) REFERENCES merchants(id);
