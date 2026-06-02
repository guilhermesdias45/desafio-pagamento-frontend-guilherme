-- V1__create_fraud_alerts.sql
-- Creates the fraud_decision enum type and fraud_alerts table.

-- ─── Enums ───────────────────────────────────────────────────────────────────

CREATE TYPE fraud_decision AS ENUM ('APPROVE', 'REVIEW', 'BLOCK');

-- ─── fraud_alerts table ──────────────────────────────────────────────────────

CREATE TABLE fraud_alerts (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id   VARCHAR(64)      NOT NULL,
    customer_id      UUID             NOT NULL,
    amount_in_cents  BIGINT           NOT NULL,
    score            INTEGER          NOT NULL,
    decision         fraud_decision   NOT NULL,
    reasons          TEXT,
    analysis_time_ms BIGINT,
    ip_address       VARCHAR(64),
    created_at       TIMESTAMPTZ      NOT NULL DEFAULT now()
);

-- ─── Indexes ─────────────────────────────────────────────────────────────────

CREATE INDEX idx_fraud_alerts_customer_id    ON fraud_alerts(customer_id);
CREATE INDEX idx_fraud_alerts_transaction_id ON fraud_alerts(transaction_id);
CREATE INDEX idx_fraud_alerts_created_at     ON fraud_alerts(created_at);
