CREATE TABLE fraud_alerts (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id  VARCHAR(50)     NOT NULL,
    customer_id     UUID            NOT NULL,
    score           INTEGER         NOT NULL CHECK (score >= 0 AND score <= 100),
    decision        VARCHAR(20)     NOT NULL CHECK (decision IN ('APPROVE', 'REVIEW', 'BLOCK')),
    reasons         JSONB,
    claude_adjustment   INTEGER,
    claude_reasoning    TEXT,
    reviewed_by     UUID,
    reviewed_at     TIMESTAMPTZ,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_fraud_alerts_customer_id ON fraud_alerts (customer_id);
CREATE INDEX idx_fraud_alerts_decision    ON fraud_alerts (decision);
CREATE INDEX idx_fraud_alerts_score       ON fraud_alerts (score);

COMMENT ON TABLE  fraud_alerts               IS 'Transações bloqueadas e em review — auditoria PCI DSS. Retidos por 2 anos.';
COMMENT ON COLUMN fraud_alerts.transaction_id IS 'Referência lógica para payment_db.transactions(id) — sem FK devido ao isolamento de dados entre serviços.';
COMMENT ON COLUMN fraud_alerts.customer_id    IS 'Referência lógica para user_db.users(id) — sem FK devido ao isolamento de dados entre serviços.';
COMMENT ON COLUMN fraud_alerts.reviewed_by    IS 'Referência lógica para user_db.users(id) — admin que revisou o alerta.';
