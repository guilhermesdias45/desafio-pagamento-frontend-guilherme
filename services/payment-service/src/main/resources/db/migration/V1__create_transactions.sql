CREATE TYPE transaction_status AS ENUM (
    'APPROVED', 'DECLINED', 'SUSPECTED_FRAUD', 'FULLY_REFUNDED', 'PARTIALLY_REFUNDED'
);

CREATE TABLE transactions (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id        VARCHAR(64) NOT NULL UNIQUE,
    mp_payment_id         BIGINT,
    customer_id           UUID NOT NULL,
    merchant_id           UUID,
    order_id              UUID,
    status                transaction_status NOT NULL,
    amount_in_cents       BIGINT NOT NULL,
    currency              VARCHAR(10) NOT NULL DEFAULT 'BRL',
    card_brand            VARCHAR(50),
    card_last_four        VARCHAR(4),
    payment_method_id     VARCHAR(50),
    installments          INTEGER NOT NULL DEFAULT 1,
    idempotency_key       UUID NOT NULL UNIQUE,
    fraud_score           INTEGER,
    fraud_decision        VARCHAR(20),
    processing_time_ms    BIGINT,
    error_code            VARCHAR(100),
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_transactions_customer_id ON transactions(customer_id);
CREATE INDEX idx_transactions_order_id ON transactions(order_id);
CREATE INDEX idx_transactions_idempotency_key ON transactions(idempotency_key);
CREATE INDEX idx_transactions_status ON transactions(status);
CREATE INDEX idx_transactions_created_at ON transactions(created_at);
