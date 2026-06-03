# Divergências Reportadas — Dev 3

**De:** Dev 1
**Data:** 2026-06-03
**Sprint:** 4

---

## D-004: `X-User-Role` (singular) vs `X-User-Roles` (plural)

**Problema:**
- api-gateway injeta `X-User-Role` (singular) via `AuthenticationFilter.java:69`
- order-service espera `X-User-Roles` (plural) em `OrderController.java`

**Correção realizada (Dev 1):** Payment-service `OrderServiceClient.java:47` alterado de `X-User-Roles` para `X-User-Role` (singular).

**Pendente (Dev 3):**
- Verificar se order-service deve aceitar `X-User-Role` (singular) ou `X-User-Roles` (plural)
- Alinhar com Dev 2 sobre o padrão definitivo

---

## D-006: Kafka topic names — alinhamento cross-service

**Eventos produzidos pelo payment-service (já implementados):**

| Tópico | Evento | Campos |
|--------|--------|--------|
| `transaction.completed` | `TransactionCompletedEvent` | `transactionId`, `mpPaymentId`, `orderId`, `customerId`, `merchantId`, `customerEmail`, `merchantEmail`, `amountInCents`, `currency`, `cardBrand`, `cardLastFour`, `installments`, `items`, `processedAt`, `status` |
| `transaction.failed` | `TransactionFailedEvent` | `transactionId`, `orderId`, `customerId`, `customerEmail`, `amountInCents`, `reason`, `createdAt`, `status` |
| `transaction.refunded` | `TransactionRefundedEvent` | `refundId`, `transactionId`, `orderId`, `customerEmail`, `amountRefundedInCents`, `isFullRefund`, `reason`, `estimatedArrivalDays`, `refundedAt` |

**Consumidores esperados:**
- order-service: `TransactionEventConsumer` — consome `transaction.completed`, `transaction.failed`, `transaction.refunded`
- notification-service: `TransactionEventConsumer` — consome `transaction.completed`, `transaction.failed`, `transaction.refunded`

**Ação necessária (Dev 3):**
- Confirmar que os consumers do order-service e notification-service esperam os mesmos campos listados acima
- Validar compatibilidade dos schemas (especialmente `TransactionCompletedEvent` que contém `cardLastFour` e `items`)
- Alinhar nomes de tópicos caso haja divergência
