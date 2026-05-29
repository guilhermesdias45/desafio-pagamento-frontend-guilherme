CREATE TABLE audit_logs (
    id              BIGSERIAL                   PRIMARY KEY,
    transaction_id  VARCHAR(64),
    action          VARCHAR(64)                 NOT NULL,
    actor_id        UUID,
    payload         JSONB,
    ip_address      VARCHAR(45),
    created_at      TIMESTAMP WITH TIME ZONE    NOT NULL DEFAULT NOW()
) WITH (FILLFACTOR = 100);

CREATE INDEX idx_audit_logs_transaction_id ON audit_logs (transaction_id);
CREATE INDEX idx_audit_logs_created_at ON audit_logs (created_at DESC);

COMMENT ON TABLE audit_logs IS 'Immutable audit trail for PCI DSS compliance';
