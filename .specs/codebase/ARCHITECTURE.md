# Architecture — Acabou o Mony

## Visão Geral

```
Internet → api-gateway(:8080) → serviços internos (rede Docker)
```

O api-gateway é o único ponto de entrada. Ele valida JWT, injeta headers (`X-User-Id`, `X-Merchant-Id`, `X-Correlation-Id`) e roteia para os serviços downstream.

## Fluxo Principal (Pagamento)

```
1. POST /api/v1/orders              → order-service (criar pedido)
2. POST /api/v1/transactions        → payment-service (processar pagamento)
   ├── fraud-service (score síncrono) → se score > 90 → BLOQUEIA
   ├── Mercado Pago API              → POST /v1/payments
   ├── Kafka → transaction.completed → order-service atualiza para PAID
   └── Kafka → transaction.completed → notification-service → email
```

**Keywords para analistas:**

| Fluxo | Keywords | Onde encontrar |
|-------|----------|----------------|
| Auth | JWT RS256, refresh token, 2FA TOTP | `specs/user-service/spec.md` |
| Pagamento | cardToken, amountInCents, idempotencyKey | `specs/payment-service/spec.md §3` |
| Estorno | partial/full refund, reason, 90d window | `specs/payment-service/spec.md §4` |
| Pedido | PENDING/PAID/EXPIRED/CANCELLED | `specs/order-service/spec.md` |
| Fraude | 9 regras, score 0-100, Claude AI borderline | `specs/fraud-service/spec.md` |
| Webhook | payment.created, payment.updated, HMAC | `specs/payment-service/spec.md` |
| Kafka | 11 tópicos, consumer groups | `specs/payment-service/spec.md §6` |

## Headers do api-gateway

| Header | Origem | Onde validar |
|--------|--------|-------------|
| `Authorization: Bearer {jwt}` | Frontend → api-gateway | `specs/user-service/spec.md §7` |
| `X-Merchant-Id` | JWT → api-gateway injeta | `specs/payment-service/spec.md §3.10` |
| `X-User-Id` | JWT → api-gateway injeta | `specs/user-service/spec.md §7.3` |
| `X-Correlation-Id` | api-gateway gera | `services/api-gateway/` |
| `Idempotency-Key` | Frontend gera (UUID) | `specs/payment-service/spec.md §3.2` |
