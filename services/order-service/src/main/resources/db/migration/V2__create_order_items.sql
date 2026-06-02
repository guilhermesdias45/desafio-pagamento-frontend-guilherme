CREATE TABLE order_items (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id            UUID NOT NULL REFERENCES orders(id),
    product_id          VARCHAR(255) NOT NULL,
    description         VARCHAR(255) NOT NULL,
    quantity            INTEGER NOT NULL,
    unit_price_in_cents BIGINT NOT NULL,
    subtotal_in_cents   BIGINT NOT NULL
);

CREATE INDEX idx_order_items_order_id ON order_items(order_id);
