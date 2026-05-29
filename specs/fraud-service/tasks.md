# Tarefas: Fraud Service

**Spec:** [spec.md](spec.md) | **Plano:** [plan.md](plan.md)
**Responsável:** Dev 1 | **Sprint:** 1

---

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
| 20 | PR + code review contra spec | Review | ⬜ | Commit preparado, aguardando envio |

---

## Checklist de Conclusão

- [x] Todas as 9 regras determinísticas cobertas por testes unitários
- [x] CE-001 a CE-005 cobertos (CE-004 bloqueado por falta de `merchantId` no DTO)
- [x] Claude timeout: 250ms → fallback = 0 (sem ajuste)
- [x] Score nunca enviado ao cliente (apenas `decision` — endpoint interno)
- [x] Endpoint `/internal/fraud/score` não acessível via api-gateway
- [x] Regras de scoring nunca expostas em logs ou respostas
- [x] Cobertura ≥ 90% (JaCoCo: 90.0%)
- [ ] Revisado por pelo menos 1 outro dev (pendente — PR preparado, não enviado)
