CREATE TYPE order_status AS ENUM (
    'PENDING', 'PROCESSING', 'PAID', 'CANCELLED', 'REFUNDED', 'PARTIALLY_REFUNDED'
);

CREATE TABLE orders (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id       UUID NOT NULL,
    merchant_id       UUID NOT NULL,
    status            order_status NOT NULL DEFAULT 'PENDING',
    total_in_cents    BIGINT NOT NULL,
    transaction_id    VARCHAR(64),
    idempotency_key   UUID NOT NULL UNIQUE,
    expires_at        TIMESTAMPTZ,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_orders_customer_id ON orders(customer_id);
CREATE INDEX idx_orders_merchant_id ON orders(merchant_id);
CREATE INDEX idx_orders_status ON orders(status);
CREATE INDEX idx_orders_idempotency_key ON orders(idempotency_key);
CREATE INDEX idx_orders_expires_at ON orders(expires_at) WHERE status = 'PENDING';
