-- =============================================================================
-- Inserir Contas de Teste MercadoPago — payment-service
-- =============================================================================
-- Execute APÓS a migration V6 rodar no banco do payment-service.
-- As senhas estão criptografadas com pgp_sym_encrypt usando a chave definida
-- em MERCADOPAGO_ENCRYPTION_KEY.
--
-- Uso:
--   1. Conecte ao banco:  psql -U aom -d payment_db
--   2. Defina a chave:    \set enc_key 'SUA_CHAVE_AES_AQUI'
--   3. Execute este script
-- =============================================================================

-- Vendedor (Seller)
INSERT INTO mp_test_accounts (type, mp_user_id, email, password_enc, verification_code)
VALUES (
    'SELLER',
    3459882808,
    'TESTUSER1504687285327688180',
    pgp_sym_encrypt('d5Poral76w', current_setting('vars.enc_key')),
    '882808'
);

-- Comprador (Buyer)
INSERT INTO mp_test_accounts (type, mp_user_id, email, password_enc, verification_code)
VALUES (
    'BUYER',
    3459473280,
    'TESTUSER2899368672786037940',
    pgp_sym_encrypt('9mGCDnDaY3', current_setting('vars.enc_key')),
    '473280'
);

-- =============================================================================
-- Alternativa sem pgp_sym_encrypt (se não tiver a chave configurada):
--
-- INSERT INTO mp_test_accounts (type, mp_user_id, email, password_enc, verification_code)
-- VALUES
--     ('SELLER', 3459882808, 'TESTUSER1504687285327688180', 'd5Poral76w', '882808'),
--     ('BUYER',  3459473280, 'TESTUSER2899368672786037940', '9mGCDnDaY3', '473280');
-- =============================================================================
