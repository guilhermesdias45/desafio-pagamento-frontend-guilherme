# Tarefas: Payment Service

**Spec:** [spec.md](spec.md) | **Plano:** [plan.md](plan.md)
**Responsável:** Dev 1 | **Sprint:** 1–2

---

## Sprint 1

| # | Tarefa | Tipo | Status | Notas |
|---|--------|------|--------|-------|
| 1 | Setup do módulo payment-service — estrutura de pacotes, dependências no pom.xml (MP SDK, Testcontainers) | Infra | ✅ | Spring Boot 3.4.5, Java 21, MapStruct, WireMock |
| 2 | Flyway V1 — tabela `transactions` com índices | Infra | ✅ | db/migration/V1__create_transactions.sql |
| 3 | Flyway V2 — tabela `refunds` | Infra | ✅ | db/migration/V2__create_refunds.sql |
| 4 | Flyway V3 — tabela `audit_logs` | Infra | ⬜ | Migration existe, mas sem código Java que escreve |
| 5 | Entidades JPA: `Transaction`, `Refund`, enums `TransactionStatus`, `RefundReason` | Code | ✅ | |
| 6 | **[TEST]** Testes unitários `TransactionService` — casos normais (aprovado, recusado) | Test | ✅ | 7 testes unitários |
| 7 | Implementar `TransactionService.processTransaction()` | Code | ✅ | Idempotência, fraud, MP gateway, Kafka |
| 8 | **[TEST]** Testes unitários — todos os casos extremos CE-001 a CE-007 da spec | Test | ⬜ | CE-007 (rate limit) implementado agora; testes pendentes |
| 9 | Implementar idempotência via Redis | Code | ✅ | TTL 24h |
| 10 | Implementar rate limiting via Redis | Code | ✅ | 100 req/min por customerId, TTL 1min (Sprint 2) |
| 11 | Implementar `FraudServiceClient` (REST para fraud-service) com timeout 250ms | Code | ✅ | |
| 12 | Implementar `MercadoPagoGateway` (adapter do SDK MP) com WireMock nos testes | Code | ✅ | |
| 13 | **[TEST]** Testes de integração com Testcontainers (PostgreSQL + Redis + Kafka) | Test | ⬜ | WireMock disponível mas testes não escritos |
| 14 | Implementar `RefundService.refundTransaction()` | Code | ✅ | Com permissão de merchant (Sprint 2) |
| 15 | **[TEST]** Testes unitários do RefundService (CE-001 a CE-004) | Test | ✅ | 5 testes, incluindo INSUFFICIENT_PERMISSIONS |
| 16 | Implementar `TransactionService.findById()` + `findByCustomer()` | Code | ✅ | Com acesso por merchant (Sprint 2) |
| 17 | **[TEST]** Testes de autorização (acesso negado a transação alheia → 403) | Test | ⬜ | Testes de controller cobrem o fluxo, mas sem cenário 403 explícito |
| 18 | Implementar `TransactionEventProducer` (Kafka: completed, failed, refunded) | Code | ✅ | |
| 19 | Implementar `MercadoPagoWebhookConsumer` (receber notificações MP) | Code | ✅ | Com validação x-signature + processamento real (Sprint 2) |
| 20 | Implementar `TransactionController` + validações Bean Validation | Code | ✅ | |
| 21 | **[TEST]** Testes de API com MockMvc | Test | ✅ | 5 testes, incluindo listTransactions |
| 22 | Configurar Dockerfile | Infra | ✅ | |
| 23 | Configurar entrada no docker-compose.yml | Infra | ✅ | |
| 24 | Validar cobertura ≥ 90% | Validate | ⬜ | JaCoCo rodando, threshold a definir |
| 25 | PR + code review contra spec | Review | ⬜ | Sprint 2 pendente |

---

## Sprint 2 (Alinhado com spec §3–8 + plan.md)

| # | Tarefa | Tipo | Status | Notas |
|---|--------|------|--------|-------|
| F1 | **Rate limiting** Redis: 100 req/min por `customerId`, key `rate_limit:payment:{customerId}`, TTL 1min | Code | ✅ | `TransactionService.processTransaction()` — `RATE_LIMIT_EXCEEDED` 429 |
| F2 | **handlePaymentWebhook() real**: processar `payment.updated` → atualizar status, `payment.cancelled` → cancelar | Code | ✅ | `TransactionService.handlePaymentWebhook()` + `findByMpPaymentId()` |
| F3 | **Validação x-signature** HMAC-SHA256 contra `MERCADOPAGO_WEBHOOK_SECRET` | Code | ✅ | `MercadoPagoWebhookConsumer.validateSignature()` — 403 se inválida |
| F4 | **Controle de acesso**: `findById(transactionId, merchantId)` + `findByCustomer(customerId, merchantId)` → retorna null se alheio, controller retorna 404 | Code | ✅ | `TransactionRepository.findByTransactionIdAndMerchantId()` + `findByCustomerIdAndMerchantIdOrderByCreatedAtDesc()` |
| F5 | **INSUFFICIENT_PERMISSIONS**: `RefundService.refund()` valida `transaction.merchantId == request.merchantId` → 403 | Code | ✅ | Spec §4.3, §4.4 CE-004 |
| F6 | **Validações de campo**: `amountInCents [1, 999999]` + `cardToken` 32 hex chars | Code | ✅ | Já existia em Sprint 1 (`@Min(1) @Max(999999)`, `@Pattern`) |

---

## Checklist de Conclusão

- [ ] Todos os CE-001 a CE-007 da spec cobertos por testes
- [ ] Estorno: CE-001 a CE-004 cobertos
- [ ] Cobertura ≥ 90%
- [ ] cardToken nunca aparece em nenhum log (verificado)
- [ ] Idempotência testada (mesma key → mesmo resultado)
- [ ] WireMock simula: MP aprovado, MP recusado, MP timeout
- [ ] Revisado por pelo menos 1 outro dev
