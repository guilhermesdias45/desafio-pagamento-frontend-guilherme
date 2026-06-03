# Sprint 4 — Planejamento Dev 1

**Responsável:** Dev 1
**Serviços:** `payment-service` (porta 8082), `fraud-service` (porta 8085)
**Período:** Sprint 4
**Roadmap:** *Production ready: load testing k6 + security audit*

---

## Escopo

Este plano cobre as tasks do Dev 1 para Sprint 4. Tasks que dependem de outros serviços **serão executadas**, e divergências encontradas com serviços fora do escopo serão registradas neste documento.

---

## Divergências Encontradas (inter-serviços)

### D-001: Endpoint `GET /internal/users/{customerId}` inexistente no user-service

| Origem | Chamada | Serviço alvo |
|--------|---------|-------------|
| `UserServiceClient.java:41` | `GET /internal/users/{customerId}` | user-service (porta 8081) |

**Problema:** O user-service **não possui** o endpoint `GET /internal/users/{customerId}`. Ele expõe apenas:
- `POST /internal/auth/validate-token` — validação de JWT (usado pelo api-gateway)
- `GET /api/v1/users/me` — perfil do usuário autenticado (requer JWT)

**Impacto:** `TransactionService.processTransaction()` chama `userClient.validateCustomer(customerId)`, que faz essa requisição. Como não existe, todas as transações cairão no `catch (Exception)` e usarão o fallback `valid=true` (aprovando o cliente). Clientes inválidos nunca serão rejeitados.

**Ação necessária:** Criar o endpoint `GET /internal/users/{customerId}` no user-service (escopo Dev 2) OU ajustar o payment-service para usar outro mecanismo de validação.

---

### D-002: URL defaults trocados no `application.yml` do payment-service

| Propriedade | Valor atual (default) | Valor esperado |
|-------------|----------------------|----------------|
| `order.service.url` | `http://localhost:8081` (porta do user-service) | `http://localhost:8083` |
| `user.service.url` | `http://localhost:8083` (porta do order-service) | `http://localhost:8081` |

**Arquivo:** `services/payment-service/src/main/resources/application.yml:46,50`

**Problema:** Os valores default das URLs estão **invertidos**. Order-service roda na porta 8083, user-service na porta 8081. Em ambiente local sem as env vars `ORDER_SERVICE_URL` e `USER_SERVICE_URL`, as chamadas vão para o serviço errado.

**Ação necessária:** Corrigir os defaults no application.yml.

---

### D-003: Internal secret com valores divergentes

| Quem envia | Valor do `X-Internal-Secret` | Configurado em |
|-----------|------------------------------|----------------|
| api-gateway (`WebClientConfig.java:18`) | `${INTERNAL_SERVICE_SECRET:dev-secret}` | `api-gateway application.yml:81` |
| payment-service (`UserServiceClient.java:36`) | `internal-payment-service` (hardcoded) | `UserServiceClient.java:36` |
| user-service (valida contra) | `${INTERNAL_SECRET:dev-internal-secret}` | `user-service application.yml:40` |

**Problema:** Três valores distintos para o mesmo secret:
- api-gateway envia `dev-secret` (via env var `INTERNAL_SERVICE_SECRET`)
- payment-service envia `internal-payment-service` (hardcoded)
- user-service espera `dev-internal-secret` (via env var `INTERNAL_SECRET`)

Nenhum dos três se coincide. Toda chamada ao user-service com `X-Internal-Secret` será rejeitada com 401.

**Ação necessária:**
- Padronizar o nome da env var (ex: `INTERNAL_SECRET` para todos)
- Payment-service deve ler de config, não hardcoded
- Ajustar api-gateway para usar a mesma env var

---

### D-004: `X-User-Role` (singular) vs `X-User-Roles` (plural)

| Quem envia | Header | Referência |
|-----------|--------|-----------|
| api-gateway `AuthenticationFilter.java:69` | `X-User-Role` (singular) | `claims.role()` |
| payment-service `OrderServiceClient.java:41` | `X-User-Roles` (plural) | hardcoded `"ADMIN"` |

**Problema:** O api-gateway injeta `X-User-Role` (singular), mas o order-service no `OrderController.java` espera `X-User-Roles` (plural). O payment-service também envia `X-User-Roles` (plural) hardcoded como `"ADMIN"` ao consultar o order-service.

**Impacto:** Este é um padrão divergente entre serviços. O header injetado pelo api-gateway não será lido pelo order-service se este esperar o nome plural. O payment-service usa hardcoded `"ADMIN"` para bypassar autorização, o que pode ser um problema de segurança.

---

### D-005: api-gateway bloqueia rotas `/internal/`

**Arquivo:** `services/api-gateway/src/main/java/com/acaboumony/gateway/filter/AuthenticationFilter.java:38-40`

```java
if (path.startsWith(INTERNAL_PREFIX)) {
    exchange.getResponse().setStatusCode(HttpStatus.NOT_FOUND);
    return exchange.getResponse().setComplete();
}
```

**Problema:** O api-gateway retorna 404 para qualquer rota que comece com `/internal/`. Isso é intencional (rotas internas não devem ser expostas publicamente), mas significa que chamadas internas como `POST /internal/fraud/score` não podem ser roteadas via api-gateway — elas devem ser chamadas **diretamente** ao fraud-service na porta 8085.

**Status:** ✅ Comportamento correto e documentado, mas relevante para os testes de carga (k6) que devem chamar o fraud-service diretamente, não via gateway.

---

### D-006: Kafka topic names — alinhamento cross-service

**Eventos produzidos pelo payment-service:**
- `transaction.completed`
- `transaction.failed`
- `transaction.refunded`

**Consumidores esperados:**
| Tópico | Consumidor | Serviço |
|--------|-----------|---------|
| `transaction.completed` | `TransactionEventConsumer` | order-service (Dev 3) |
| `transaction.failed` | `TransactionEventConsumer` | order-service (Dev 3) |
| `transaction.refunded` | `TransactionEventConsumer` | order-service (Dev 3) |
| `transaction.completed` | `TransactionEventConsumer` | notification-service (Dev 3) |
| `transaction.failed` | `TransactionEventConsumer` | notification-service (Dev 3) |
| `transaction.refunded` | `TransactionEventConsumer` | notification-service (Dev 3) |

**Status:** ✅ Aparentemente alinhado — verificar durante os testes de integração com Kafka real (Testcontainers).

---

## Tasks da Sprint

### Fase 1 — Security Audit / PCI DSS

| # | Task | Serviço | Tipo | Esforço | Dependência |
|---|------|---------|------|---------|-------------|
| 1.1 | Auditar logs: verificar se `cardToken`, `cardLastFour` nunca aparecem em logs | payment | Audit | 1h | Nenhuma |
| 1.2 | Auditar logs: verificar se `ipAddress` está sempre anonimizado (`anonymizeIp()`) | fraud | Audit | 0.5h | Nenhuma |
| 1.3 | Auditar logs: verificar se regras de scoring não vazam em logs ou responses | fraud | Audit | 0.5h | Nenhuma |
| 1.4 | Auditar logs: verificar se `GlobalExceptionHandler` não expõe stack trace ou dados sensíveis | payment | Audit | 0.5h | Nenhuma |
| 1.5 | Verificar circuit breaker + fallback em `FraudServiceClient`, `MercadoPagoGateway`, `ClaudeContextAnalyzerImpl` | ambos | Audit | 1h | Nenhuma |
| 1.6 | Verificar idempotência via Redis (TTL 24h, key `idempotency:payment:`) | payment | Audit | 0.5h | Nenhuma |
| 1.7 | Verificar rate limiting (100 req/min, key `rate_limit:payment:`) com fallback Redis indisponível | payment | Audit | 0.5h | Nenhuma |
| 1.8 | Gerar relatório PCI DSS + LGPD em `qa-output/dev1/pci-report.md` | ambos | Doc | 1h | Nenhuma |

---

### Fase 2 — Load Testing com k6

| # | Task | Serviço | Tipo | Esforço | Dependência | Notas |
|---|------|---------|------|---------|-------------|-------|
| 2.1 | Criar script k6: `POST /internal/fraud/score` — APPROVE, BLOCK, REVIEW | fraud | Test | 2h | Nenhuma (chamada direta, sem gateway) | Testa os 3 cenários de decisão |
| 2.2 | Criar script k6: `POST /api/v1/transactions` direto ao payment-service | payment | Test | 2h | WireMock para mockar order/user/fraud | Bypass do api-gateway via profile de teste com WireMock |
| 2.3 | Criar script k6: `GET /api/v1/transactions/{id}` + listagem paginada | payment | Test | 1h | Nenhuma (consulta direta ao banco) | |
| 2.4 | Criar script k6: `POST /api/v1/transactions/{id}/refund` | payment | Test | 1h | WireMock para mockar MP | |
| 2.5 | Configurar thresholds k6 (`http_req_duration` P99 < alvo) | ambos | Config | 0.5h | Nenhuma | |
| 2.6 | Executar bateria e documentar resultados em `docs/performance/sprint-4-results.md` | ambos | Doc | 1h | Nenhuma | |
| 2.7 | Criar script k6: fluxo end-to-end simulando cliente real (via api-gateway) | ambos | Test | 2h | ⚠️ api-gateway (Dev 2), user-service (Dev 2), order-service (Dev 3) | Requer todos os serviços rodando — validar divergências D-001, D-002, D-003, D-004 antes |

---

### Fase 3 — Testcontainers (habilitar testes de integração)

| # | Task | Serviço | Tipo | Esforço | Dependência |
|---|------|---------|------|---------|-------------|
| 3.1 | Verificar se `BaseIntegrationTest` de ambos sobe PostgreSQL 16, Redis 7, Kafka | ambos | Test | 0.5h | Docker Desktop |
| 3.2 | Garantir que `TransactionServiceIntegrationTest` (12 testes) está sem `@Disabled` | payment | Test | 0.5h | Docker Desktop |
| 3.3 | Garantir que `FraudDetectionServiceIntegrationTest` (8 testes) está sem `@Disabled` | fraud | Test | 0.5h | Docker Desktop |
| 3.4 | Adicionar `@Tag("integration")` e configurar surefire para excluir em `mvn test` | ambos | Config | 0.5h | Nenhuma |
| 3.5 | Rodar `mvn verify` com Testcontainers e validar JaCoCo ≥ 90% | ambos | Validate | 1h | Docker Desktop |
| 3.6 | Validar que eventos Kafka produzidos pelo payment-service são consumíveis pelo order-service e notification-service (verificar formato dos eventos) | payment | Test | 1h | ⚠️ order-service (Dev 3), notification-service (Dev 3) | Alinhar formato dos eventos Kafka (D-006) |

---

### Fase 4 — Pendências Técnicas

| # | Task | Serviço | Tipo | Esforço | Dependência |
|---|------|---------|------|---------|-------------|
| 4.1 | Verificar se `AuditLog` é populado em todos os pontos (CREATED, FRAUD_CHECK, GATEWAY_TIMEOUT, PAYMENT_FAILED, PAYMENT_APPROVED, REFUND) | payment | Code | 1h | Nenhuma |
| 4.2 | Verificar se `safeRedisDelete()` é chamado em todos os pontos de falha | payment | Code | 0.5h | Nenhuma |
| 4.3 | Verificar se `cardBrand`, `cardLastFour`, `installments` são persistidos corretamente | payment | Code | 0.5h | Nenhuma |
| 4.4 | Verificar se `TransactionSummary` retorna campos corretos na listagem | payment | Code | 0.5h | Nenhuma |
| 4.5 | Atualizar `specs/payment-service/tasks.md` com status real | payment | Doc | 0.5h | Nenhuma |
| 4.6 | Atualizar `specs/fraud-service/tasks.md` com status real | fraud | Doc | 0.5h | Nenhuma |

---

### Fase 5 — Correção de Divergências (próprio escopo)

| # | Task | Serviço | Tipo | Esforço | Dependência | Ref. |
|---|------|---------|------|---------|-------------|------|
| 5.1 | Corrigir URL defaults invertidos no `application.yml` (`order.service.url` → 8083, `user.service.url` → 8081) | payment | Fix | 0.5h | Nenhuma | D-002 |
| 5.2 | Externalizar `X-Internal-Secret` do payment-service para `application.yml` (remover hardcoded `internal-payment-service`) | payment | Fix | 0.5h | Nenhuma | D-003 |
| 5.3 | Alinhar nome do header de role para `X-User-Role` (singular) no `OrderServiceClient` | payment | Fix | 0.5h | ⚠️ Verificar se order-service espera plural ou singular | D-004 |

---

### Fase 6 — Divergências a reportar (fora do escopo de correção)

Estas tasks consistem em **abrir chamados/documentar** as divergências encontradas em serviços de outros devs.

| # | Task | Serviço alvo | Dono | Ref. |
|---|------|-------------|------|------|
| 6.1 | Reportar necessidade de criar endpoint `GET /internal/users/{customerId}` no user-service | user-service | Dev 2 | D-001 |
| 6.2 | Reportar divergência de nomes de env var do internal secret (`INTERNAL_SERVICE_SECRET` vs `INTERNAL_SECRET`) | api-gateway | Dev 2 | D-003 |
| 6.3 | Reportar divergência `X-User-Role` (singular) vs `X-User-Roles` (plural) entre api-gateway e order-service | api-gateway + order-service | Dev 2 + Dev 3 | D-004 |
| 6.4 | Alinhar formato dos eventos Kafka com Dev 3 (order-service + notification-service consumers) | order-service + notification-service | Dev 3 | D-006 |

---

## Ordem de Execução Sugerida

```
Fase 1 (Security Audit)          → 8 tasks  → ~4h  → 0 dependências
Fase 4 (Pendências Técnicas)     → 6 tasks  → ~3h  → 0 dependências
Fase 5 (Correções próprio escopo) → 3 tasks  → ~1.5h → 0 dependências
Fase 3 (Testcontainers)          → 6 tasks  → ~4h  → Docker + Dev 3 (Kafka format)
Fase 2 (k6 Load Tests)           → 7 tasks  → ~7.5h → WireMock / Dev 2 + Dev 3 (e2e)
Fase 6 (Reportar divergências)   → 4 tasks  → ~2h  → reportar para Dev 2 + Dev 3
                                    ---
                          Total: 34 tasks  → ~22h
```

---

## Critérios de Aceitação da Sprint

- [ ] Relatório PCI DSS gerado em `qa-output/dev1/pci-report.md` sem críticos
- [ ] Scripts k6 criados em `scripts/k6/` para payment e fraud
- [ ] Thresholds k6 validados: payment P99 < 1000ms, fraud P99 < 200ms
- [ ] Testcontainers rodando sem `@Disabled` em ambos os serviços
- [ ] `mvn verify` com cobertura JaCoCo ≥ 90% em ambos
- [ ] `AuditLog` populado em todos os pontos do fluxo
- [ ] Defaults de URL corrigidos no `application.yml`
- [ ] Internal secret externalizado para config
- [ ] `tasks.md` de ambos os serviços atualizados
- [ ] Divergências D-001 a D-006 documentadas e reportadas aos devs responsáveis
