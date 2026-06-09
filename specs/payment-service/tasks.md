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
| 8 | **[TEST]** Testes unitários — todos os casos extremos CE-001 a CE-007 da spec | Test | ✅ | CE-001 a CE-007 cobertos: unit + controller (Rate Limit: 429 + Retry-After) |
| 9 | Implementar idempotência via Redis | Code | ✅ | TTL 24h |
| 10 | Implementar rate limiting via Redis | Code | ✅ | 100 req/min por customerId, TTL 1min (Sprint 2) |
| 11 | Implementar `FraudServiceClient` (REST para fraud-service) com timeout 250ms | Code | ✅ | |
| 12 | Implementar `MercadoPagoGateway` (adapter do SDK MP) com WireMock nos testes | Code | ✅ | |
| 13 | **[TEST]** Testes de integração com Testcontainers (PostgreSQL + Redis + Kafka) | Test | ❌ | Cancelado — Docker Desktop 29.x named pipes incompatível com Testcontainers 1.20.6 |
| 14 | Implementar `RefundService.refundTransaction()` | Code | ✅ | Com permissão de merchant (Sprint 2) |
| 15 | **[TEST]** Testes unitários do RefundService (CE-001 a CE-004) | Test | ✅ | 5 testes, incluindo INSUFFICIENT_PERMISSIONS |
| 16 | Implementar `TransactionService.findById()` + `findByCustomer()` | Code | ✅ | Com acesso por merchant (Sprint 2) |
| 17 | **[TEST]** Testes de autorização (acesso negado a transação alheia → 403) | Test | ✅ | ControllerTest: getTransaction 403 (notFound + unauthorized), refundTransaction 403 |
| 18 | Implementar `TransactionEventProducer` (Kafka: completed, failed, refunded) | Code | ✅ | |
| 19 | Implementar `MercadoPagoWebhookConsumer` (receber notificações MP) | Code | ✅ | Com validação x-signature + processamento real (Sprint 2) |
| 20 | Implementar `TransactionController` + validações Bean Validation | Code | ✅ | |
| 21 | **[TEST]** Testes de API com MockMvc | Test | ✅ | 5 testes, incluindo listTransactions |
| 22 | Configurar Dockerfile | Infra | ✅ | |
| 23 | Configurar entrada no docker-compose.yml | Infra | ✅ | |
| 24 | Validar cobertura ≥ 90% | Validate | ✅ | JaCoCo 0.8.14 (upgrade de 0.8.12 p/ Java 26), 90% LINE threshold atingido |
| 25 | PR + code review contra spec | Review | ✅ | Merge concluído |

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

## Sprint 3 (Resilience, Observability, Compliance)

| # | Tarefa | Tipo | Status | Notas |
|---|--------|------|--------|-------|
| S1 | **Circuit Breaker Resilience4j** — `FraudServiceClient.score()` + `MercadoPagoGateway{createPayment,refundPayment}` com fallback | Code | ✅ | Programático `CircuitBreaker.executeSupplier()`; `resilience4j-spring-boot3:2.2.0` |
| S2 | **Custom Micrometer metrics** — counters `payment.transactions.approved/failed`, timer `payment.transactions.processing.time` | Code | ✅ | `MeterRegistry` injetado no `TransactionService` |
| S3 | **Configurable MP timeout** — `${mercadopago.timeout-ms:800}` | Code | ✅ | `MercadoPagoGateway.TIMEOUT_MS` lê de `application.yml` |
| S4 | **Redis cache TTL** 5min → 60s | Code | ✅ | Cache de categorias com TTL reduzido |
| S5 | **Audit logs em refunds** — `AuditLogRepository` injetado no `RefundService` | Code | ✅ | Compliance — Épico 2 |
| S6 | **Webhook secret ausente → 401** (antes: log warning + processava) | Code | ✅ | Épico 5 — segurança |
| S7 | **Dead code removal** — 4 exception classes deletadas (`CardDeclinedException`, etc.) + pom.xml exclusions | Code | ✅ | Épico 7 |
| S8 | **JaCoCo upgrade** 0.8.12 → 0.8.14 (Java 26 compat) + IT exclusion | Infra | ✅ | `mvn verify` com 166 testes, 90% LINE |
| S9 | **[TEST]** Circuit breaker tests — `FraudServiceClientTest`, `MercadoPagoGateway{Test,MockedTest}`, `ClaudeContextAnalyzerImplTest` | Test | ✅ | Todos com `CircuitBreakerRegistry.ofDefaults()` |
| S10 | **Health indicators** — Redis, PostgreSQL, Kafka, MP Gateway | Code | ✅ | `@Component` health indicators em ambos os serviços |

---

## Sprint 4 (Security Audit, Correções, Performance)

| # | Tarefa | Tipo | Status | Notas |
|---|--------|------|--------|-------|
| S11 | **Security Audit PCI DSS** — logs, circuit breaker, idempotência, rate limiting | Audit | ✅ | Relatório em `qa-output/dev1/pci-report.md` |
| S12 | **Corrigir URL defaults invertidos** `order.service.url` → 8083, `user.service.url` → 8081 | Fix | ✅ | D-002 |
| S13 | **Externalizar `X-Internal-Secret`** do payment-service para `application.yml` | Fix | ✅ | D-003: config `payment.internal-secret` lê `INTERNAL_SECRET` env var |
| S14 | **Alinhar header de role** `X-User-Roles` → `X-User-Role` (singular) no `OrderServiceClient` | Fix | ✅ | D-004 |
| S15 | **Adicionar circuit breaker** em `UserServiceClient` e `OrderServiceClient` | Fix | ✅ | Instâncias `userService` e `orderService` no resilience4j |
| S16 | **Corrigir raw IP em logs** — `IpBlacklistRule.java` e `FraudDetectionService.java` | Fix | ✅ | PCI DSS 3.4 |
| S17 | **Criar scripts k6** — fraud score, transações, consulta, refund, e2e | Test | ✅ | Sprint 4 — ver `scripts/k6/` |
| S18 | **Testcontainers** — verificar `@Disabled` e configurar `@Tag("integration")` | Config | ✅ | Sprint 4 |

## Checklist de Conclusão (Sprint 4)

- [x] Defaults de URL corrigidos no `application.yml`
- [x] Internal secret externalizado para config (D-003)
- [x] Circuit breaker nos 5 clients (fraudService, mercadoPago, claudeApi, userService, orderService)
- [x] PCI report gerado em `qa-output/dev1/pci-report.md` sem críticos
- [x] Scripts k6 criados em `scripts/k6/` para payment e fraud
- [x] Testcontainers rodando sem `@Disabled`
- [x] `mvn verify` com cobertura JaCoCo ≥ 90%
- [x] `AuditLog` populado em todos os pontos do fluxo
- [x] `tasks.md` atualizado

---

## Sprint 5 (Contas de Teste MercadoPago)

**Referência:** Spec §9, [`sprint-5-mp-test-accounts.md`](sprint-5-mp-test-accounts.md)

| # | Tarefa | Tipo | Status | Notas |
|---|--------|------|--------|-------|
| T1 | Atualizar spec §9 — Contas de Teste MP (spec.md) | Spec | ✅ | Seção 9 adicionada |
| T2 | Atualizar plan — tabela, configs, riscos (plan.md) | Plan | ✅ | Tabela, migrations, riscos |
| T3 | V6 Migration — `mp_test_accounts` | Infra | ✅ | `V6__create_mp_test_accounts.sql` |
| T4 | Entidade `MpTestAccount` + enum `MpAccountType` | Code | ✅ | `domain/entity/MpTestAccount.java`, `domain/enums/MpAccountType.java` |
| T5 | Repository `MpTestAccountRepository` | Code | ✅ | `repository/MpTestAccountRepository.java` |
| T6 | DTO `MpTestAccountResponse` | Code | ✅ | `dto/response/MpTestAccountResponse.java` |
| T7 | **[TEST]** `MpTestAccountRepositoryIT` | Test | ✅ | Extends `BaseIntegrationTest` (Docker required) |
| T8 | **[TEST]** `MpTestAccountTest` | Test | ✅ | 6 tests, entity unit tests |
| T9 | `MercadoPagoGateway` overload com seller token | Code | ✅ | `createPayment()` com `@Nullable sellerAccessToken` + `MPRequestOptions` |
| T10 | **[TEST]** `MercadoPagoGatewayMockedTest` — overload | Test | ✅ | 3 new tests for overload behavior |
| T11 | `TransactionService` — usar `test@testuser.com` | Code | ✅ | `@Value("${mercadopago.payer-email}")` injetado |
| T12 | **[TEST]** `TransactionServiceTest` — atualizar | Test | ✅ | Novo teste `processTransaction_usesPayerEmailFromConfig` |
| T13 | Script SQL manual `insert-mp-test-accounts.sql` | Infra | ✅ | `scripts/insert-mp-test-accounts.sql` |
| T14 | Validar cobertura JaCoCo ≥ 90% | Validate | ⬜ | Requer Docker para `mvn verify` (83% unit-only) |
