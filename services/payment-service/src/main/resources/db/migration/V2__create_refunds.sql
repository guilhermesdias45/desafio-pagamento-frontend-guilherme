CREATE TABLE refunds (
    id                      BIGSERIAL       PRIMARY KEY,
    refund_id               VARCHAR(64)     NOT NULL UNIQUE,
    transaction_id          VARCHAR(64)     NOT NULL REFERENCES transactions(transaction_id),
    amount_in_cents         BIGINT          NOT NULL CHECK (amount_in_cents > 0),
    is_full_refund          BOOLEAN         NOT NULL DEFAULT TRUE,
    reason                  VARCHAR(64)     NOT NULL,
    requested_by            UUID            NOT NULL,
    idempotency_key         UUID            NOT NULL UNIQUE,
    status                  VARCHAR(32)     NOT NULL,
    estimated_arrival_days  INTEGER,
    processed_at            TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_refunds_transaction_id ON refunds (transaction_id);
CREATE INDEX idx_refunds_idempotency_key ON refunds (idempotency_key);
