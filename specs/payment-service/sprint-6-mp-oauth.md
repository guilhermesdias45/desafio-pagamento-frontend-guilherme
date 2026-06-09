# Sprint 6 — OAuth MercadoPago (Fase 2 — Token Seller)

**Spec:** [spec.md](spec.md) §9 | **Plano:** [plan.md](plan.md) | **Tasks:** [tasks.md](tasks.md)
**Responsável:** Dev 1 | **Status:** Em andamento

---

## 1. Objetivo

Implementar o fluxo OAuth `authorization_code` para obter o `access_token` do seller do MercadoPago,
armazená-lo criptografado na tabela `mp_test_accounts`, e utilizá-lo nas chamadas de pagamento
ao gateway — permitindo que a transação ocorra entre as contas de teste seller e buyer.

## 2. Fluxo OAuth

```
┌─────────────┐          ┌──────────────────┐          ┌────────────────┐
│  Admin      │─────────▶│  /authorize      │─────────▶│  MP OAuth URL  │
│  (navegador)│          │  (redirect 302)  │          │  (login MP)    │
└─────────────┘          └──────────────────┘          └────────────────┘
                                                               │
                                                               │ (user authorizes)
                                                               ▼
┌─────────────┐          ┌──────────────────┐          ┌────────────────┐
│  Admin      │◀────────│  /callback        │◀─────────│  Redirect URI  │
│  (navegador)│          │  (code → token)  │          │  ?code=xxx     │
└─────────────┘          └──────────────────┘          └────────────────┘
                               │
                               ▼
                    ┌──────────────────────┐
                    │  POST /oauth/token   │
                    │  → access_token      │
                    │  → refresh_token     │
                    │  → expires_in        │
                    └──────────────────────┘
                               │
                               ▼
                    ┌──────────────────────┐
                    │  mp_test_accounts    │
                    │  (access_token_enc)  │
                    └──────────────────────┘
```

## 3. Tasks

| # | Tarefa | Tipo | Status |
|---|--------|------|--------|
| O1 | MpEncryptionService — AES-256/GCM para tokens | Code | ⬜ |
| O2 | [TEST] MpEncryptionServiceTest | Test | ⬜ |
| O3 | MpOAuthConfig — Client ID, Secret, Redirect URI | Code | ⬜ |
| O4 | MpOAuthService — auth URL + token exchange + refresh | Code | ⬜ |
| O5 | [TEST] MpOAuthServiceTest | Test | ⬜ |
| O6 | MpOAuthController — GET /authorize e GET /callback | Code | ⬜ |
| O7 | [TEST] MpOAuthControllerTest | Test | ⬜ |
| O8 | MpTestAccountService — buscar seller + decriptar token | Code | ⬜ |
| O9 | [TEST] MpTestAccountServiceTest | Test | ⬜ |
| O10 | TransactionService — injetar sellerAccessToken | Code | ⬜ |
| O11 | [TEST] TransactionServiceTest — seller token | Test | ⬜ |
| O12 | Atualizar application.yml + test yml + .env.example | Config | ⬜ |
| O13 | Compilar + testes unitários (187+ → ~210) | Validate | ⬜ |

## 4. Configs

```yaml
mercadopago:
  oauth:
    client-id: ${MP_CLIENT_ID}
    client-secret: ${MP_CLIENT_SECRET}
    redirect-uri: "http://localhost:8082/api/v1/admin/mp-oauth/callback"
    auth-url: "https://auth.mercadopago.com.br/authorization"
    token-url: "https://api.mercadopago.com/oauth/token"
  encryption:
    key: ${MERCADOPAGO_ENCRYPTION_KEY}
```

## 5. Decisões Técnicas

| Decisão | Escolha | Motivo |
|---------|---------|--------|
| Criptografia | AES-256/GCM com IV prefixado + Base64 | Segurança em repouso, sem dependência externa |
| HTTP Client | RestTemplate (Spring Boot) | Simples, já disponível no classpath |
| Armazenamento | access_token_enc na linha do seller | Tabela já existe com os campos |
| Refresh automático | Na chamada ao gateway, se expirado | Evita duplicar lógica de refresh |
| Fallback | Se token seller ausente/vazio → usa token global | Compatibilidade com Fase 1 |
