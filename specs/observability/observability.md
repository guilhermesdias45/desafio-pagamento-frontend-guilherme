# Especificação: Observabilidade — Micrometer + New Relic

**SPEC-OBS-001** | Sprint 4 | Status: APROVADO

---

## 1. Assinatura

Não se aplica (cross-cutting concern de infraestrutura, sem API pública).

Endpoints expostos via Spring Boot Actuator:
- `GET /actuator/health` — health check (já existente)
- `GET /actuator/metrics` — catálogo de métricas (já existente)
- `GET /actuator/prometheus` — métricas em formato Prometheus **(novo)**

---

## 2. Tipos de Dados

### Métricas de negócio já implementadas

**payment-service** (`TransactionService`):
| Métrica | Tipo | Tags | Descrição |
|---------|------|------|-----------|
| `payment.approved` | Counter | — | Transações aprovadas |
| `payment.failed` | Counter | — | Transações rejeitadas |
| `payment.processing.time` | Timer | — | Latência de processamento |

**fraud-service** (`FraudDetectionService`):
| Métrica | Tipo | Tags | Descrição |
|---------|------|------|-----------|
| `fraud.decision.approve` | Counter | — | Análises aprovadas |
| `fraud.decision.review` | Counter | — | Análises para revisão |
| `fraud.decision.block` | Counter | — | Análises bloqueadas |
| `fraud.analysis.time` | Timer | — | Latência da análise de fraude |

### Tags globais (aplicadas a todas as métricas)
| Tag | Valor | Fonte |
|-----|-------|-------|
| `application` | `${spring.application.name}` | application.yml |
| `environment` | `${APP_ENV:local}` | env var |

### Rastreamento distribuído
- `traceId` e `spanId` propagados automaticamente via MDC em todos os logs
- Sampling configurável via `TRACING_SAMPLE_RATE` (padrão: 1.0 = 100%)

---

## 3. Pré-condições

- Todos os 6 serviços devem ter `spring-boot-starter-actuator` (já existe)
- Para exportar para New Relic: `NEW_RELIC_LICENSE_KEY` + `NEW_RELIC_ACCOUNT_ID` + `NEW_RELIC_ENABLED=true`
- Endpoint Prometheus (`/actuator/prometheus`) disponível sempre, sem credenciais externas

---

## 4. Pós-condições (Sucesso)

- `GET /actuator/prometheus` retorna 200 com métricas em formato text/plain OpenMetrics
- Com New Relic habilitado: métricas publicadas em push a cada 30s para New Relic Insights API
- `traceId` presente em todos os logs estruturados (campo `trace_id` no JSON)
- Métricas de negócio (transações aprovadas/bloqueadas, latências) visíveis no dashboard New Relic

---

## 5. Pós-condições (Erro)

| Cenário | Comportamento |
|---------|---------------|
| `NEW_RELIC_ENABLED=false` (padrão) | New Relic registry desativado; sem conexão externa; Prometheus segue funcionando |
| `NEW_RELIC_LICENSE_KEY` inválida | Exportação para New Relic falha silenciosamente; erro no log; serviço continua |
| `NEW_RELIC_ACCOUNT_ID` inválido | Idem |
| Endpoint OTLP indisponível | Tracing degrada graciosamente; spans descartados sem impacto no serviço |

---

## 6. Invariantes

- A adição dos exportadores não altera o comportamento funcional de nenhum serviço
- Exportação para New Relic **não deve** bloquear threads de negócio (assíncrona por design do Micrometer)
- Nenhum dado sensível (PAN, CPF, senha, token) deve aparecer em tags ou nomes de métricas
- `NEW_RELIC_ENABLED=false` por padrão — deve ser opt-in em produção

---

## 7. Casos Extremos

| ID | Input | Comportamento esperado |
|----|-------|----------------------|
| CE-001 | New Relic desabilitado (`false`) | `NewRelicMeterRegistry` não é instanciado; `/actuator/prometheus` funciona normalmente |
| CE-002 | API key vazia | Spring Boot não cria o registry se `api-key` for vazio; no-op |
| CE-003 | Timeout na API New Relic | Micrometer descarta o batch e loga warning; não bloqueia |
| CE-004 | Múltiplos registries (NR + Prometheus) | `CompositeMeterRegistry` gerencia ambos transparentemente |
| CE-005 | `TRACING_SAMPLE_RATE=0.0` | Nenhum span coletado; `traceId` não propagado |

---

## 8. Exemplos Concretos

### Exemplo 1 — endpoint Prometheus (sempre disponível)

```
GET /actuator/prometheus
→ 200 OK
Content-Type: text/plain; version=0.0.4; charset=utf-8

# HELP payment_approved_total
# TYPE payment_approved_total counter
payment_approved_total{application="payment-service",environment="local",} 42.0
# HELP fraud_analysis_time_seconds
# TYPE fraud_analysis_time_seconds summary
fraud_analysis_time_seconds_count{application="fraud-service",environment="local",} 100.0
fraud_analysis_time_seconds_sum{application="fraud-service",environment="local",} 14.3
```

### Exemplo 2 — New Relic habilitado em produção

```
# .env
NEW_RELIC_LICENSE_KEY=abc123...
NEW_RELIC_ACCOUNT_ID=1234567
NEW_RELIC_ENABLED=true
APP_ENV=production

→ A cada 30s: POST https://metric-api.newrelic.com/metric/v1
  com métricas de todos os serviços taggeadas com environment=production
```

### Exemplo 3 — traceId nos logs

```json
{
  "timestamp": "2026-06-10T11:00:00.000Z",
  "level": "INFO",
  "logger": "com.acaboumony.payment.service.TransactionService",
  "message": "Transaction txn_abc123 approved in 312ms",
  "traceId": "4bf92f3577b34da6a3ce929d0e0e4736",
  "spanId": "00f067aa0ba902b7"
}
```

---

## 9. Efeitos Colaterais

- **Push de métricas** para New Relic: a cada 30s quando habilitado (conexão HTTPS de saída)
- **Scrape Prometheus**: passivo, sem efeito colateral (pull quando requisitado)
- **Tracing**: spans criados em memória e descartados (sem exporter OTLP configurado nesta fase)
- **Tags globais**: adicionadas automaticamente a todas as métricas registradas

---

## 10. Performance

| Item | Impacto estimado |
|------|-----------------|
| Overhead de métricas | < 1ms por operação (operações in-memory) |
| Push New Relic | Assíncrono, 0ms impacto no request path |
| Endpoint `/actuator/prometheus` | < 10ms para serializar métricas |
| Tracing (bridge OTEL) | < 0.5ms por span (in-memory, sem exportação) |

---

## 11. Segurança

- `NEW_RELIC_LICENSE_KEY` nunca deve ser logada (tratada como segredo)
- `/actuator/prometheus` e `/actuator/metrics` expostos internamente (sem auth nesta fase)
  - Em produção: proteger via Spring Security ou restricão de rede
- Tags de métricas: apenas `application` e `environment` — sem dados de usuário

---

## Dependências adicionadas (todas sem versão explícita — gerenciadas pelo Spring Boot BOM 3.4.5)

```xml
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-new-relic</artifactId>
</dependency>
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-tracing-bridge-otel</artifactId>
</dependency>
```

## Configuração por serviço (application.yml)

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    prometheus:
      enabled: true
  newrelic:
    metrics:
      export:
        enabled: ${NEW_RELIC_ENABLED:false}
        api-key: ${NEW_RELIC_LICENSE_KEY:disabled}   # "disabled" é o fallback; evita erro de validação
        account-id: ${NEW_RELIC_ACCOUNT_ID:0}        # "0" é o fallback
        step: 30s
  metrics:
    tags:
      application: ${spring.application.name}
      environment: ${APP_ENV:local}
  tracing:
    sampling:
      probability: ${TRACING_SAMPLE_RATE:1.0}
```

> **Nota sobre o namespace:** Spring Boot 3.4 migrou de `management.metrics.export.new-relic.*`
> para `management.newrelic.metrics.export.*`. Os valores de fallback `disabled`/`0` evitam
> que o Micrometer lance `ValidationException` durante inicialização. Com `enabled=false`,
> nenhum dado é exportado.
