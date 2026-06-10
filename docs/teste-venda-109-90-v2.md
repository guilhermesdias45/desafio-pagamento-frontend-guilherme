# Teste E2E: Venda R$ 109,90 entre Contas de Teste Mercado Pago

## Histórico de Alterações

| Data | Versão | Descrição | Autor |
|------|--------|-----------|-------|
| 2026-06-10 | 1.0 | Criação do documento com planejamento completo | agente |

---

## 1. Objetivo

Validar ponta a ponta uma venda de R$ 109,90 entre um comprador (BUYER) e um vendedor (SELLER) test users do Mercado Pago, utilizando:

- OAuth via ngrok para obter o access token do SELLER
- `payment-service` API para processar o pagamento
- Confirmação de que os fundos foram para a conta SELLER (não para o app owner)

---

## 2. Premissas e Restrições

- **Client ID** e **Client Secret** registrados no MP Dashboard, injetados via env vars (`MP_CLIENT_ID`, `MP_CLIENT_SECRET`)
- ngrok como túnel HTTPS público para redirect URI do OAuth
- Tokens criptografados em repouso com AES-256/GCM (Java)
- Seller token obtido exclusivamente via OAuth `authorization_code`
- Seller token injetado por request via `MercadoPagoGateway.createPayment(..., sellerAccessToken)`
- Fallback para global token quando seller token indisponível
- JaCoCo coverage ≥ 90% (requer Docker para testes de integração)
- **Não fazer refund** — apenas validação de venda + verificação de saldos

---

## 3. Plano de Execução

### Fase 1 — Criar Test Users com APP_USR

| # | Ação | Quem | Status |
|---|---|---|---|
| 1.1 | Criar BUYER: `POST /users/test` com `site_id: "MLB"` + token `APP_USR-...` | agente | ✅ Skip — já existente |
| 1.2 | Criar SELLER: `POST /users/test` com `site_id: "MLB"` | agente | ✅ Skip — já existente |
| 1.3 | Inserir ambos em `mp_test_accounts` (id, email, nickname, type) | agente | ✅ Já inserido na sessão anterior |
| 1.4 | Verificar: `SELECT * FROM mp_test_accounts` | agente | ✅ OK — 2 registros (BUYER + SELLER) |

### Fase 2 — ngrok + redirect_uri

| # | Ação | Quem | Status |
|---|---|---|---|
| 2.1 | Iniciar ngrok: `ngrok http http://localhost:8082` | agente | ✅ OK |
| 2.2 | Capturar URL: `https://squabble-engulf-ocean.ngrok-free.dev` | agente | ✅ OK |
| 2.3 | Registrar URL no MP Dashboard como redirect_uri da sua app | **você** | ✅ OK |
| 2.4 | Atualizar `MP_OAUTH_REDIRECT_URI` no `docker-compose.yml` + `application.yml` | agente | ✅ OK |
| 2.5 | Rebuild + restart payment-service | agente | ✅ OK |

### Fase 3 — OAuth (obter token do seller)

| # | Ação | Quem | Status |
|---|---|---|---|
| 3.1 | Abrir `https://squabble-engulf-ocean.ngrok-free.dev/api/v1/admin/mp-oauth/authorize` no navegador | **você** | ✅ OK |
| 3.2 | Logar como SELLER test user (email `TESTUSER1504687285327688180`, senha `882808`) | **você** | ✅ OK |
| 3.3 | Clicar "Permitir" | **você** | ✅ OK |
| 3.4 | Callback captura code, troca por token, persiste criptografado | automático | ✅ OK |
| 3.5 | Verificar: `SELECT access_token_enc, token_expires_at FROM mp_test_accounts WHERE type='SELLER'` | agente | ✅ OK |

### Fase 4 — Vender R$ 109,90 e Verificar Saldos

| # | Ação | Quem | Status |
|---|---|---|---|
| 4.1 | Gerar card token APRO (Mastercard 5031...) | agente | ❌ Pendente |
| 4.2 | Criar order R$ 109,90 via SQL em `order_service.orders` | agente | ❌ Pendente |
| 4.3 | `POST /api/v1/transactions` com card token + order | agente | ❌ Pendente |
| 4.4 | Verificar `status: "APPROVED"` e `mpPaymentId` | agente | ❌ Pendente |
| 4.5 | Consultar pagamento no MP e confirmar: | agente | ❌ Pendente |
| | • `collector_id` = ID do SELLER test user | | |
| | • `net_received_amount` > 0 | | |
| | • `payer.email` = email do BUYER | | |
| 4.6 | Verificar `transactions` no banco: `status, amount` | agente | ❌ Pendente |
| 4.7 | Verificar order: `status = 'PAID'` (SQL direto, Kafka issue conhecida) | agente | ❌ Pendente |

---

## 4. Critérios de Aceite

- [ ] CE-001: `POST /users/test` com APP_USR retorna test users (200 OK)
- [ ] CE-002: ngrok tunnel HTTPS responde ao healthcheck do payment-service
- [ ] CE-003: OAuth salva seller token criptografado no banco
- [ ] CE-004: `POST /api/v1/transactions` → `status: "APPROVED"`, HTTP 201
- [ ] CE-005: `collector_id` no pagamento MP = ID do SELLER test user
- [ ] CE-006: `net_received_amount` > 0 nos detalhes do pagamento

---

## 5. Decisões Técnicas

| Decisão | Escolha | Motivo |
|---|---|---|
| Criação test users | APP_USR token de produção | Único token aceito pelo `POST /users/test` |
| redirect_uri | ngrok HTTPS | MP exige HTTPS e URL pública |
| OAuth exchange | `test_token: false` (default) | Queremos token SANDBOX para test user |
| Atualizar order status | SQL direto | Kafka event broken (`ClassNotFoundException`) |
| Verificar `collector_id` | Campo nativo da resposta MP | Única forma confiável de provar transferência |

---

## 6. Ambiente

- **SO:** Windows 11 / PowerShell 5.1
- **Containers:** Docker Compose (10 containers)
- **ngrok:** v3.39.7, authtoken registrado
- **MP:** Sandbox (test mode)
- **payment-service:** `http://localhost:8082`

---

## 7. Log de Execução

### 2026-06-10 — Documento criado

**O quê:** Criação do plano completo de validação E2E.

**Como:** Documento estruturado com 4 fases, 6 critérios de aceite e decisões técnicas, consolidando todo o planejamento discutido entre agente e usuário.

### 2026-06-10 — Fase 1: Verificação dos Test Users

**O quê:** Verificado que os test users BUYER (3459473280) e SELLER (3459882808) criados na sessão anterior ainda estão ativos no Mercado Pago e no banco `payment_db`.

**Como:**
- `docker exec aom-postgres psql -U aom -d payment_db -c "SELECT * FROM mp_test_accounts"` → 2 registros, BUYER e SELLER
- `GET https://api.mercadopago.com/users/3459882808` e `3459473280` com `TEST-...` token → ambos `user_type: normal, site_id: MLB, status: active`
- Token `APP_USR-5504c1cb-4a77-4f74-8bff-014975241ac2` retornou `403 PA_UNAUTHORIZED_RESULT_FROM_POLICIES` ao tentar criar novos test users — mas não precisamos de novos
- Os test users existentes têm `access_token_enc` e `refresh_token_enc` vazios (esperado — seller token só via OAuth na Fase 3)

### 2026-06-10 — Fase 2: Início — ngrok tunnel ativo

**O quê:** Iniciado ngrok tunnel apontando para `http://localhost:8082` (payment-service).

**Como:**
- `Start-Process -WindowStyle Hidden -FilePath $ngrokExe -ArgumentList "http http://localhost:8082"`
- Verificado via `GET http://127.0.0.1:4040/api/tunnels` → URL: `https://squabble-engulf-ocean.ngrok-free.dev`
- `application.yml` atualizado: `redirect-uri` agora usa `${MP_OAUTH_REDIRECT_URI:http://localhost:8082/...}` (env var com fallback local)
- `.env` atualizado com `MP_OAUTH_REDIRECT_URI=https://squabble-engulf-ocean.ngrok-free.dev/api/v1/admin/mp-oauth/callback`

**Aguardando:** Usuário registrar `https://squabble-engulf-ocean.ngrok-free.dev/api/v1/admin/mp-oauth/callback` como **redirect_uri** no MP Dashboard (Applications → sua app → Edit → Redirect URIs), com `test_token: false` (default).

### 2026-06-10 — Fase 2: Completa — ngrok + redirect_uri configurados

**O quê:** Configurado e verificado o túnel ngrok + redirect_uri do OAuth.

**Como:**
- `application.yml` atualizado: `redirect-uri: ${MP_OAUTH_REDIRECT_URI:http://localhost:8082/api/v1/admin/mp-oauth/callback}` (env var com fallback)
- `docker-compose.yml` atualizado: `MP_OAUTH_REDIRECT_URI: ${MP_OAUTH_REDIRECT_URI}` adicionado ao environment do payment-service
- `.env` atualizado: `MP_OAUTH_REDIRECT_URI=https://squabble-engulf-ocean.ngrok-free.dev/api/v1/admin/mp-oauth/callback`
- Rebuild e restart: `docker compose --profile app up --build -d payment-service`
- Verificado: `curl.exe -v http://localhost:8082/api/v1/admin/mp-oauth/authorize` → HTTP 302 com Location apontando para `auth.mercadopago.com.br/authorization?client_id=6025083406574896&redirect_uri={ngrok}/api/v1/admin/mp-oauth/callback&state=...`
- Health check: `localhost:8082/actuator/health` → `UP` (DB, Redis, Kafka) ✅

### 2026-06-10 — Fase 3: OAuth concluído — seller token salvo

**O quê:** Usuário abriu URL de autorização, logou como SELLER test user e autorizou o app. Callback processou o code e salvou o token criptografado.

**Como:**
- `SELECT * FROM mp_test_accounts WHERE type='SELLER'` → `access_token_enc` preenchido (136 bytes), `token_expires_at = 2026-12-07 14:45:08` (válido ~6 meses)
- O token não precisa ser acessado manualmente — o `MpTestAccountService` o injeta automaticamente nas chamadas ao `MercadoPagoGateway`

### 2026-06-10 — Fase 4: Início — tentativa de transação R$ 109,90

**O quê:** Tentativa de processar venda de R$ 109,90 entre BUYER e SELLER test users.

**Passos:**
1. **Card token gerado** com `public_key=APP_USR-86b075f3-a77f-4340-a60c-2d4acc47b697` (produção):
   - Token: `463c0e6ef0efaa3756614c13b3cbaa3e`, `live_mode: true`
2. **Order criada** no `order_service.orders`:
   - `id = ac0a6809-61d9-4936-b475-54e6ab0f441a`
   - `customer_id = 7f50a202-4480-4363-a0ee-5f80112d461e` (cliente.pag@teste.com)
   - `merchant_id = efdd4ef7-bf54-438a-8ade-0f3ad80ee8a5` (lojista.pag@teste.com)
   - `total_in_cents = 10990`, `status = PENDING`
3. **POST /api/v1/transactions** → erro 422 `CARD_DECLINED`
   - Fraude retornou fallback score 50 (OK, não bloqueou)
   - MP API retornou 400, mas não foi possível ver o body completo do erro
4. **Card token sandbox gerado** com `access_token=TEST-...`:
   - Token: `01db0be4c31e660986ca03726c1d2060`, `live_mode: false`
   - MP API retornou "Invalid card token" (luhn_validation: false)
5. **Problema identificado:** Card token APP_USR (`live_mode: true`) não funciona com access token TEST (sandbox), e card token gerado com access_token não é válido (luhn_validation false)
6. **Aguardando:** Public Key de TESTE do MP Dashboard (formato `TEST-xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx`) para gerar card token sandbox válido
