# Especificação: Detectar Fraude

**ID:** SPEC-FRD-001  
**Serviço:** fraud-service  
**Status:** Aprovado  
**Revisores:** [x] PM [x] Arquiteto [x] QA [x] Security

---

## 1. Assinatura

```java
// Endpoint interno — não exposto ao público
POST /internal/fraud/score
Headers:
  X-Internal-Secret: {secret}
Body: FraudAnalysisRequest

// Service
public FraudScore score(FraudAnalysisRequest request)
```

Chamado exclusivamente pelo `payment-service` de forma síncrona, antes de qualquer chamada ao gateway Mercado Pago.

---

## 2. Tipos de Dados

### Input — FraudAnalysisRequest

```java
public record FraudAnalysisRequest(
    @NotBlank String transactionId,
    @NotNull UUID customerId,
    @NotNull UUID merchantId,
    @NotNull @Min(1) Long amountInCents,
    @NotBlank String paymentMethodId,
    @NotBlank String ipAddress,
    String deviceFingerprint,   // opcional
    Double latitude,            // opcional
    Double longitude            // opcional
) {}
```

### Output — FraudScore (HTTP 200)

```java
public record FraudScore(
    int score,               // 0–100 (0 = sem risco, 100 = fraude certa)
    String decision,         // "APPROVE" | "REVIEW" | "BLOCK"
    List<String> reasons,    // fatores de risco — nunca enviados ao cliente final
    long analysisTimeMs
) {}
```

### Thresholds de Decisão

| Score   | Decisão  | Ação                                                  |
|---------|----------|-------------------------------------------------------|
| 0 – 69  | APPROVE  | Transação segue para o gateway                        |
| 70 – 89 | REVIEW   | Segue para o gateway + análise contextual via Claude  |
| 90 – 100| BLOCK    | Transação bloqueada; evento `fraud.detected` publicado|

---

## 3. Pré-condições

- Header `X-Internal-Secret` válido (autenticação entre serviços)
- `customerId` é um UUID válido
- `amountInCents` ≥ 1
- `ipAddress` é um IPv4 ou IPv6 válido

---

## 4. Pós-condições (Sucesso)

- Score calculado e retornado sincroneamente
- Velocity counters atualizados no Redis (`velocity:{customerId}` TTL 5 min)
- Se `decision = BLOCK`: evento `fraud.detected` publicado no Kafka; `FraudAlert` gravado no PostgreSQL
- Se `decision = REVIEW`: `FraudAlert` gravado no PostgreSQL para acompanhamento
- Se `decision = APPROVE`: nenhum efeito colateral persistido

---

## 5. Pós-condições (Erro)

| Código                  | HTTP | Retryable | Descrição                              |
|-------------------------|------|-----------|----------------------------------------|
| MISSING_INTERNAL_SECRET | 401  | false     | Header X-Internal-Secret ausente       |
| INVALID_INTERNAL_SECRET | 403  | false     | Secret inválido                        |
| INVALID_REQUEST         | 400  | false     | Campos obrigatórios ausentes ou inválidos |
| ANALYSIS_TIMEOUT        | 503  | true      | Análise excedeu 250ms — fallback ativo |

---

## 6. Invariantes

1. Score nunca é compartilhado com o cliente final — apenas a `decision` chega ao payment-service
2. Regras determinísticas são **sempre** aplicadas, independentemente da disponibilidade do Claude AI
3. Se a análise completa ultrapassar 250ms → retornar `score=50, decision=APPROVE` com log de alerta (fail-safe)
4. Mesmo input produz mesmo output determinístico (regras determinísticas sem randomness)
5. Dados de análise retidos por 2 anos para auditoria PCI DSS
6. Score nunca ultrapassa 100 nem fica abaixo de 0, mesmo com acúmulo de regras

---

## 7. Casos Extremos

| ID     | Input                                                            | Comportamento                                             | Output                             |
|--------|------------------------------------------------------------------|-----------------------------------------------------------|------------------------------------|
| CE-001 | Primeiro cliente (sem histórico)                                 | Score base 20; sem histórico para comparar                | APPROVE (score ≤ 69)               |
| CE-002 | Claude AI indisponível (timeout ou erro)                        | Aplicar só regras determinísticas; log de alerta          | Score base sem ajuste contextual   |
| CE-003 | IP em blacklist + velocity alta + novo dispositivo + R$999,99   | Múltiplas regras acumulam; score pode atingir 100         | BLOCK, evento `fraud.detected`     |
| CE-004 | Score borderline (REVIEW) com cliente de histórico longo e limpo| Claude reduz score em até -10; pode mover para APPROVE    | APPROVE se score ajustado cair < 70|
| CE-005 | IP em blacklist, cliente com histórico limpo                     | Regras determinísticas têm precedência; +40 mantido       | Score mínimo 30 mesmo após ajuste  |
| CE-006 | Análise ultrapassa 250ms                                         | Interromper, retornar score conservador                   | score=50, APPROVE, log de alerta   |

---

## 8. Exemplos Concretos

### Exemplo 1 — Transação aprovada (baixo risco)

**Request:**
```json
{
  "transactionId": "txn_tmp_001",
  "customerId": "550e8400-e29b-41d4-a716-446655440000",
  "merchantId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
  "amountInCents": 8990,
  "paymentMethodId": "visa",
  "ipAddress": "200.175.10.1"
}
```

**Response HTTP 200:**
```json
{
  "score": 5,
  "decision": "APPROVE",
  "reasons": [],
  "analysisTimeMs": 45
}
```

### Exemplo 2 — Transação bloqueada (fraude clara)

**Request:** IP em blacklist + 5 transações nos últimos 2 min + novo dispositivo + R$999,99

**Response HTTP 200:**
```json
{
  "score": 95,
  "decision": "BLOCK",
  "reasons": ["IP_BLACKLISTED", "VELOCITY_EXCEEDED", "NEW_DEVICE_HIGH_VALUE"],
  "analysisTimeMs": 62
}
```

### Exemplo 3 — REVIEW com ajuste contextual do Claude

**Contexto:** Novo dispositivo (sem fingerprint anterior) + horário 03h + R$450,00.  
Score base determinístico: 75 (REVIEW). Claude analisa histórico: cliente com 18 meses, padrão regular, sem ocorrências → ajuste -10.

**Response HTTP 200:**
```json
{
  "score": 65,
  "decision": "APPROVE",
  "reasons": ["NEW_DEVICE", "UNUSUAL_HOUR"],
  "analysisTimeMs": 182
}
```

---

## 9. Efeitos Colaterais

| Efeito                                    | Quando        | Síncrono | Obrigatório     |
|-------------------------------------------|---------------|----------|-----------------|
| Atualizar velocity counters no Redis      | Sempre        | Sim      | Sim             |
| Gravar FraudAlert no PostgreSQL           | BLOCK, REVIEW | Sim      | Sim             |
| Publicar `fraud.detected` no Kafka        | BLOCK         | Não      | Best-effort     |
| Chamar Claude AI para ajuste contextual   | REVIEW        | Sim (c/ timeout) | Não (fallback) |
| Adicionar IP à blacklist por 24h          | BLOCK por IP  | Sim      | Sim             |

---

## 10. Performance

| Etapa                              | P50   | P99    |
|------------------------------------|-------|--------|
| Regras determinísticas             | 10ms  | 30ms   |
| Redis velocity checks              | 5ms   | 20ms   |
| Claude AI — ajuste contextual      | 100ms | 180ms  |
| **Total (APPROVE / BLOCK)**        | **20ms** | **60ms** |
| **Total (REVIEW com Claude)**      | **120ms** | **200ms** |

**Timeout máximo:** 250ms — se exceder, retornar `score=50, decision=APPROVE` com log de alerta.

---

## 11. Segurança

- Endpoint `/internal/fraud/score` protegido por `X-Internal-Secret` — não exposto ao público
- Regras de pontuação nunca expostas via API (anti-gaming)
- Score nunca incluído na resposta ao cliente final — apenas a decisão chega ao payment-service
- IP anonimizado nos logs (últimos 8 bits zerados): `200.175.10.0` em vez de `200.175.10.x`
- Dados de análise retidos por 2 anos para auditoria PCI DSS
- Blacklist de IPs gerenciada com TTL configurável (default 24h)
- Regras revisadas mensalmente com dados rotulados pela equipe de segurança

### Regras Determinísticas de Pontuação

| Regra                                              | Pontos |
|----------------------------------------------------|--------|
| Velocity: 3+ transações em 5 min (mesmo cliente)  | +30    |
| Valor 5× acima da média histórica do cliente       | +25    |
| IP em blacklist                                    | +40    |
| País do IP diferente do país do cadastro           | +20    |
| Novo dispositivo + valor acima de R$500            | +15    |
| Mesmo cartão em 3+ contas diferentes               | +35    |
| Horário incomum (02h–05h) + valor acima de R$300   | +10    |
| Primeira compra + valor máximo (R$999,99)          | +20    |
| IP mudou em menos de 1 min para o mesmo cliente    | +25    |
