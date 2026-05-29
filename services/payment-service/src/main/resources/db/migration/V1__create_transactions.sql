CREATE TABLE transactions (
    id              BIGSERIAL       PRIMARY KEY,
    transaction_id  VARCHAR(64)     NOT NULL UNIQUE,
    mp_payment_id   BIGINT,
    order_id        UUID            NOT NULL,
    customer_id     UUID            NOT NULL,
    merchant_id     UUID            NOT NULL,
    amount_in_cents BIGINT          NOT NULL CHECK (amount_in_cents > 0),
    currency        VARCHAR(3)      NOT NULL DEFAULT 'BRL',
    card_brand      VARCHAR(32),
    card_last_four  VARCHAR(4),
    installments    INTEGER         DEFAULT 1,
    payment_method_id VARCHAR(32)   NOT NULL,
    status          VARCHAR(32)     NOT NULL,
    error_code      VARCHAR(64),
    error_message   TEXT,
    idempotency_key UUID            NOT NULL UNIQUE,
    processing_time_ms BIGINT,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_transactions_customer_id ON transactions (customer_id);
CREATE INDEX idx_transactions_order_id ON transactions (order_id);
CREATE INDEX idx_transactions_status ON transactions (status);
CREATE INDEX idx_transactions_created_at ON transactions (created_at DESC);
