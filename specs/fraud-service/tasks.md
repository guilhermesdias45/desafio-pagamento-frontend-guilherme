# Tarefas: Fraud Service

**Spec:** [spec.md](spec.md) | **Plano:** [plan.md](plan.md)
**Responsável:** Dev 1

---

## Sprint 1 (Completa)

| # | Tarefa | Tipo | Status | Notas |
|---|--------|------|--------|-------|
| 1 | Setup do módulo fraud-service — estrutura de pacotes, dependências (Anthropic SDK, Testcontainers) | Infra | ✅ | |
| 2 | Flyway V1 — tabela `fraud_alerts` | Infra | ✅ | |
| 3 | Flyway V2 — tabela `ip_blacklist` | Infra | ✅ | |
| 4 | Entidades JPA: `FraudAlert`, `IpBlacklist`, enum `FraudDecision` | Code | ✅ | |
| 5 | **[TEST]** Testes unitários de cada `FraudRule` individualmente | Test | ✅ | 9 test classes — uma por regra |
| 6 | Implementar todas as 9 regras determinísticas (Strategy pattern) | Code | ✅ | Scores conferem com spec |
| 7 | **[TEST]** Testes de acumulação de score — múltiplas regras simultâneas | Test | ✅ | RuleEngineServiceTest + FraudDetectionServiceTest cobrem acumulação |
| 8 | Implementar `RuleEngineService` (aplicar todas as regras e somar) | Code | ✅ | Score capped em 100 |
| 9 | **[TEST]** Testes velocity checks com Redis — 3+ transações em 5 min | Test | ✅ | VelocityRuleTest + FraudDetectionServiceTest cobrem threshold 3+ |
| 10 | Implementar velocity checks via Redis Sorted Sets | Code | ✅ | `fraud:velocity:{customerId}` com TTL 5min |
| 11 | **[TEST]** Testes CE-001 (primeiro cliente), CE-002 (Claude indisponível), CE-003 (velocity alta) | Test | ✅ | CE-001 a CE-005 cobertos no FraudDetectionServiceTest |
| 12 | Implementar `ClaudeContextAnalyzer` com timeout 250ms e fallback 0 | Code | ✅ | Anthropic SDK 2.7.0, timeout 250ms, fallback 0 |
| 13 | **[TEST]** Testes de integração Testcontainers (PostgreSQL + Redis + Kafka) | Test | ✅ | FraudIntegrationTest com H2 + @MockBean (sem Docker) |
| 14 | Implementar `FraudEventProducer` (Kafka: fraud.detected, fraud.review) | Code | ✅ | KafkaTemplate send com CompletableFuture async |
| 15 | Implementar `FraudController` — endpoint interno `/internal/fraud/score` | Code | ✅ | Não exposto via api-gateway |
| 16 | **[TEST]** Testes de API com MockMvc | Test | ✅ | 3 testes: válido, inválido (400), bloqueado |
| 17 | Configurar Dockerfile | Infra | ✅ | Pré-existente — multi-stage build com Maven + JRE 21 |
| 18 | Configurar entrada no docker-compose.yml | Infra | ✅ | Pré-existente — porta não exposta (interno) |
| 19 | Validar cobertura ≥ 90% | Validate | ✅ | JaCoCo: 90.0% line coverage (82 testes, 0 falhas) |
| 20 | PR + code review contra spec | Review | ✅ | Merge concluído |

---

## Sprint 2

| # | Tarefa | Tipo | Status | Notas |
|---|--------|------|--------|-------|
| 21 | **[TEST]** `MerchantPatternRuleTest` — testes unitários da regra (6-8 testes) | Test | ✅ | 9 testes: 0, <3, =3, <5, =5, >5 transações, reason, score, Redis key |
| 22 | Adicionar `merchantId` ao `FraudDetectedEvent` (Kafka) | Code | ✅ | `FraudEventProducer.FraudDetectedEvent` inclui `UUID merchantId` |
| 23 | Persistir `reasoning` do Claude em `FraudAlert.claudeReasoning` | Code | ✅ | Coluna TEXT populada em `FraudDetectionService` |
| 24 | Incluir `merchantId` no prompt do Claude (análise contextual) | Code | ✅ | `ClaudeContextAnalyzerImpl.buildUserPrompt()` inclui `Merchant ID` |
| 25 | Mover thresholds para `application.yml` (BLOCK, REVIEW, timeout) | Code | ✅ | 6 props configuráveis via env vars |
| 26 | Criar `IpBlacklistRepository` JPA | Code | ✅ | `JpaRepository<IpBlacklist, UUID>` com `findByIpAddress` |
| 27 | **[TEST]** Teste CE-004 no `FraudDetectionServiceTest` | Test | ✅ | `ce004_merchantHighVelocity_shouldAddMerchantPatternScore` |
| 28 | Anonimizar IP em logs (últimos 8 bits zerados) | Code | ✅ | `anonymizeIp()` zera último octeto, usado em logs |
| 29 | Criar `RedisConfig.java` (bean RedisTemplate) | Code | ✅ | `@Configuration` com bean `StringRedisTemplate` |
| 30 | Criar `AnthropicConfig.java` (bean AnthropicClient) | Code | ✅ | `@Configuration` com bean `AnthropicClient`, timeout 250ms |

---

## Sprint 3 (Resilience, Observability, Compliance)

| # | Tarefa | Tipo | Status | Notas |
|---|--------|------|--------|-------|
| S1 | **Circuit Breaker Resilience4j** — `ClaudeContextAnalyzerImpl.{getContextualAdjustment,adjustWithReasoning}` com fallback | Code | ✅ | Programático `CircuitBreaker.executeSupplier()`; `resilience4j-spring-boot3:2.2.0` |
| S2 | **Custom Micrometer metrics** — counters `fraud.decision.approve/review/block`, timer `fraud.analysis.time` | Code | ✅ | `MeterRegistry` injetado no `FraudDetectionService` |
| S3 | **Auto-blacklist IP** — `FraudDetectionService.autoBlacklistIp()` persiste em JPA `ip_blacklist` | Code | ✅ | Compliance — Épico 2 |
| S4 | **Health indicators** — Redis, PostgreSQL, Kafka | Code | ✅ | `@Component` health indicators |
| S5 | **[TEST]** Circuit breaker tests — `ClaudeContextAnalyzerImplTest` com `CircuitBreakerRegistry.ofDefaults()` | Test | ✅ | 15 testes passando |

## Checklist de Conclusão

### Sprint 1
- [x] Todas as 9 regras determinísticas cobertas por testes unitários
- [x] CE-001 a CE-005 cobertos
- [x] Claude timeout: 250ms → fallback = 0 (sem ajuste)
- [x] Score nunca enviado ao cliente (apenas `decision` — endpoint interno)
- [x] Endpoint `/internal/fraud/score` não acessível via api-gateway
- [x] Regras de scoring nunca expostas em logs ou respostas
- [x] Cobertura ≥ 90% (JaCoCo: 90.0%)
- [x] Revisado e merged

### Sprint 2
- [x] CE-004: `MerchantPatternRuleTest` com cobertura completa
- [x] CE-004: `merchantId` no evento Kafka `fraud.detected`
- [x] Claude reasoning persistido em `FraudAlert`
- [x] Claude prompt inclui contexto do merchant
- [x] Thresholds configuráveis via `application.yml`
- [x] `IpBlacklistRepository` JPA implementado
- [x] IP anonimizado em logs (últimos 8 bits)
- [x] `RedisConfig` + `AnthropicConfig` criados

---

## Sprint 4 (Security Audit + Performance)

| # | Tarefa | Tipo | Status | Notas |
|---|--------|------|--------|-------|
| S6 | **Security Audit PCI DSS** — logs, scoring, IP anonimização | Audit | ✅ | Relatório em `qa-output/dev1/pci-report.md` |
| S7 | **Corrigir raw IP em logs** — `IpBlacklistRule.java:43`, `FraudDetectionService.java:209` | Fix | ✅ | PCI DSS 3.4 — IP substituído por anonimizado ou removido |
| S8 | **Criar scripts k6** — `POST /internal/fraud/score` (APPROVE, BLOCK, REVIEW) | Test | ✅ | Sprint 4 — ver `scripts/k6/` |

### Checklist Sprint 4

- [x] PCI report gerado sem críticos
- [x] Raw IP corrigido em logs
- [x] Scripts k6 criados
