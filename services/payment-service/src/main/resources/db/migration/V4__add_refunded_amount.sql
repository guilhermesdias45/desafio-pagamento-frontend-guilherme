ALTER TABLE transactions ADD COLUMN refunded_amount_in_cents BIGINT DEFAULT 0;

UPDATE transactions SET refunded_amount_in_cents = 0 WHERE refunded_amount_in_cents IS NULL;
