# Sprint 4 — Resultados de Performance

**Data:** 2026-06-03
**Responsável:** Dev 1
**Serviços:** payment-service (8082), fraud-service (8085)

---

## Thresholds Definidos

| Serviço | Métrica | Threshold | Status |
|---------|---------|-----------|--------|
| fraud-service | `http_req_duration` P99 | < 200ms | ⏳ Pendente |
| payment-service | `http_req_duration` P99 | < 1000ms | ⏳ Pendente |
| fraud-service | `http_req_failed` | < 1% | ⏳ Pendente |
| payment-service | `http_req_failed` | < 1% | ⏳ Pendente |

---

## Scripts k6 Criados

| Script | Endpoint | Cenários | Arquivo |
|--------|----------|----------|---------|
| `fraud-score.js` | `POST /internal/fraud/score` | APPROVE, BLOCK, REVIEW | `scripts/k6/fraud-score.js` |
| `create-transaction.js` | `POST /api/v1/transactions` | Criação de pagamento | `scripts/k6/create-transaction.js` |
| `get-transaction.js` | `GET /api/v1/transactions/{id}` + listagem | Consulta e listagem | `scripts/k6/get-transaction.js` |
| `refund-transaction.js` | `POST /api/v1/transactions/{id}/refund` | Estorno | `scripts/k6/refund-transaction.js` |
| `e2e-flow.js` | Fluxo via api-gateway | Login → pagamento → consulta | `scripts/k6/e2e-flow.js` |

---

## Resultados

> ⚠️ *Bateria de testes não executada — requer todos os serviços rodando (docker-compose up) e WireMock configurado para simular dependências externas.*

### Para executar:

```bash
# Fraud service (chamada direta, sem gateway)
k6 run scripts/k6/fraud-score.js

# Payment service (requer WireMock para mockar order/user/fraud)
k6 run scripts/k6/create-transaction.js

# Consulta
k6 run scripts/k6/get-transaction.js

# Estorno
k6 run scripts/k6/refund-transaction.js

# Fluxo e2e (requer todos os serviços + api-gateway)
k6 run scripts/k6/e2e-flow.js
```

### Observações

- Fraud-service deve ser chamado **diretamente** na porta 8085, não via api-gateway (D-005)
- Payment-service deve ser chamado com profile de teste que usa WireMock para mockar dependências
- End-to-end requer user-service (Dev 2) e order-service (Dev 3) rodando
