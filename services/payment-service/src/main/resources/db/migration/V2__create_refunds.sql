CREATE TYPE refund_status AS ENUM ('PENDING', 'COMPLETED', 'FAILED');
CREATE TYPE refund_reason AS ENUM ('CUSTOMER_REQUEST', 'DUPLICATE', 'FRAUD', 'PRODUCT_NOT_DELIVERED');

CREATE TABLE refunds (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id        VARCHAR(64) NOT NULL REFERENCES transactions(transaction_id),
    amount_in_cents       BIGINT NOT NULL,
    reason                refund_reason NOT NULL,
    status                refund_status NOT NULL DEFAULT 'PENDING',
    requested_by          UUID NOT NULL,
    mp_refund_id          BIGINT,
    idempotency_key       UUID NOT NULL UNIQUE,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_refunds_transaction_id ON refunds(transaction_id);
