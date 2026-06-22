# Integrations — Acabou o Mony

## APIs que o Frontend Consome

Todas as chamadas passam pelo api-gateway (`localhost:8080`). O frontend NUNCA chama serviços diretamente.

### Endpoints Públicos (sem JWT)

| Endpoint | Método | Body/Params | Spec de referência |
|----------|--------|-------------|-------------------|
| `/api/v1/auth/register` | POST | `{email, password, fullName, role, companyName?, cnpj?}` | `specs/user-service/spec.md §3` |
| `/api/v1/auth/confirm-email` | POST | `{email, token}` | `specs/user-service/spec.md §3` |
| `/api/v1/auth/login` | POST | `{email, password, totpCode?}` | `specs/user-service/spec.md §4` |
| `/api/v1/auth/refresh` | POST | Cookie `refreshToken` | `specs/user-service/spec.md §5` |

### Endpoints Autenticados (Bearer JWT)

| Endpoint | Método | Headers adicionais | Spec |
|----------|--------|-------------------|------|
| `/api/v1/auth/logout` | POST | — | `specs/user-service/spec.md` |
| `/api/v1/auth/2fa/setup` | POST | — | `specs/user-service/spec.md §6` |
| `/api/v1/auth/2fa/confirm` | POST | — | `specs/user-service/spec.md §6` |
| `/api/v1/auth/2fa/verify` | POST | — | `specs/user-service/spec.md §6` |
| `/api/v1/auth/2fa/disable` | POST | — | `specs/user-service/spec.md §6` |
| `/api/v1/auth/2fa/recovery` | POST | — | `specs/user-service/spec.md §6` |
| `/api/v1/users/me` | GET | — | `specs/user-service/spec.md` |
| `/api/v1/orders` | POST | `X-Merchant-Id, Idempotency-Key` | `specs/order-service/spec.md` |
| `/api/v1/orders` | GET | `?status=&page=&size=` | `specs/order-service/spec.md` |
| `/api/v1/orders/{id}` | GET | — | `specs/order-service/spec.md` |
| `/api/v1/transactions` | POST | `X-Merchant-Id, X-Forwarded-For, Idempotency-Key` | `specs/payment-service/spec.md §3` |
| `/api/v1/transactions/{id}` | GET | — | `specs/payment-service/spec.md §5` |
| `/api/v1/transactions` | GET | `?customerId=&page=&size=` | `specs/payment-service/spec.md §5` |
| `/api/v1/transactions/{id}/refund` | POST | `X-Merchant-Id, Idempotency-Key` | `specs/payment-service/spec.md §4` |

### Integração Externa (Mercado Pago JS SDK)

O frontend precisa gerar **card tokens** chamando o SDK do Mercado Pago diretamente no navegador:

```js
const mp = new MercadoPago('TEST-PUBLIC-KEY', { locale: 'pt-BR' });
const cardToken = await mp.cardToken({
  cardNumber: '5031 4332 1540 6351',
  expirationMonth: '12',
  expirationYear: '2030',
  securityCode: '123',
  cardholderName: 'APRO'
});
// cardToken.id → usado no POST /api/v1/transactions
```

Referência: `docs/teste-r25-webhook.md` (passo 5) e `docs/teste-r50-webhook.md` (passo 9).

### Formatos de Resposta (Padrão)

```typescript
// Sucesso
{
  data: { ... },        // Payload específico do endpoint
  meta: {
    timestamp: "2026-...",
    requestId: "req_..."
  },
  errors: []
}

// Erro
{
  data: null,
  meta: { ... },
  errors: [{
    code: "ERROR_CODE",
    message: "Mensagem amigável",
    retryable: true/false
  }]
}
```

### Divergências Conhecidas

| ID | Problema | Impacto | Onde ver |
|----|----------|---------|----------|
| D-001 | `GET /internal/users/{id}` não existe no user-service | Payment-service retorna `valid=true` para todo customer | `docs/sprints/divergencias-dev3.md` |
| D-004 | `X-User-Role` vs `X-User-Roles` (singular/plural) | Pode causar 403 no gateway | `docs/sprints/divergencias-dev2.md` |
| D-007 | Fraud timeout 250ms vs 1156ms real | Circuit breaker opens, fallback approve sem fraude | `docs/sprints/divergencias-dev3.md` |
