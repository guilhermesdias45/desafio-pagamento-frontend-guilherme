CREATE TABLE ip_blacklist (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    ip_address      VARCHAR(45)     NOT NULL,
    reason          TEXT,
    source          VARCHAR(50)     NOT NULL CHECK (source IN ('MANUAL', 'AUTOMATIC', 'FRAUD_DETECTED')),
    expires_at      TIMESTAMPTZ,
    created_by      UUID,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX idx_ip_blacklist_ip_address ON ip_blacklist (ip_address);

COMMENT ON TABLE  ip_blacklist                  IS 'IPs com histórico confirmado de fraude. Expiração configurável (default 24h).';
COMMENT ON COLUMN ip_blacklist.ip_address       IS 'Suporte a IPv4 e IPv6 (VARCHAR(45)).';
COMMENT ON COLUMN ip_blacklist.source           IS 'Origem: MANUAL (admin), AUTOMATIC (regra de velocity), FRAUD_DETECTED (score ≥ 90).';
COMMENT ON COLUMN ip_blacklist.expires_at       IS 'NULL = permanente. Default: NOW() + 24h via aplicação.';
COMMENT ON COLUMN ip_blacklist.created_by       IS 'Referência lógica para user_db.users(id) — admin que adicionou manualmente.';
