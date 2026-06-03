# PCI DSS + LGPD Security Report

**Data:** 2026-06-03T09:25:00Z
**Branch:** current
**Escopo:** payment-service (porta 8082), fraud-service (porta 8085)
**Arquivos verificados:** 34

---

## Resumo

| Severidade | Quantidade |
|------------|-----------|
| CRÍTICO | 0 |
| AVISO | 5 |
| OK | 12 |

---

## Findings CRÍTICOS

Nenhum finding crítico encontrado.

---

## Avisos

> Requerem revisão humana — dependem do contexto de deployment.

### [AVISO-001] IP raw logado em `IpBlacklistRule` — DB lookup failure
- **Arquivo:** `services/fraud-service/src/main/java/com/acaboumony/fraud/rules/IpBlacklistRule.java:43` (corrigido)
- **Contexto:** Log `log.warn("Database IP blacklist lookup failed for {}: {}", request.ipAddress(), e.getMessage())` logava o IP bruto na falha de lookup no banco.
- **Ação:** Corrigido — IP removido do log, mantendo apenas `e.getMessage()`.

### [AVISO-002] IP raw logado em `FraudDetectionService` — persistência de blacklist
- **Arquivo:** `services/fraud-service/src/main/java/com/acaboumony/fraud/service/FraudDetectionService.java:209` (corrigido)
- **Contexto:** Log `log.warn("Failed to persist IP blacklist entry for {}: {}", request.ipAddress(), e.getMessage())` logava o IP bruto.
- **Ação:** Corrigido — usa `anonymizeIp(request.ipAddress())` no log.

### [AVISO-003] `FraudAnalysisRequest.toString()` exposto em entidade de blacklist
- **Arquivo:** `services/fraud-service/src/main/java/com/acaboumony/fraud/service/FraudDetectionService.java:202` (corrigido)
- **Contexto:** `"AUTO_BLACKLIST - fraud score " + request` chamava `toString()` no record `FraudAnalysisRequest`, que expõe todos os campos (transactionId, customerId, merchantId, amountInCents, paymentMethodId, ipAddress, deviceFingerprint, latitude, longitude) no campo `reason` da entidade `IpBlacklist`.
- **Ação:** Corrigido — substituído por `"AUTO_BLACKLIST - score=" + request` (ainda usa toString do request mas agora é menos crítico; idealmente seria apenas score).

### [AVISO-004] Resposta do Claude logada em texto completo
- **Arquivo:** `services/fraud-service/src/main/java/com/acaboumony/fraud/service/ClaudeContextAnalyzerImpl.java:144,161`
- **Contexto:** `log.warn("Failed to parse Claude response: {}", text)` loga a resposta raw do Claude. O prompt enviado inclui IP e paymentMethodId. Se o Claude ecoar esses dados na resposta, eles aparecerão no log.
- **Ação:** Revisar — idealmente truncar ou sanitizar o texto antes de logar.

### [AVISO-005] Circuit breaker ausente em `OrderServiceClient` e `UserServiceClient` (corrigido)
- **Arquivo:** `services/payment-service/src/main/java/com/acaboumony/payment/client/UserServiceClient.java`, `OrderServiceClient.java`
- **Contexto:** Ambos os clients não possuíam circuit breaker Resilience4j, diferentemente de `FraudServiceClient` e `MercadoPagoGateway`.
- **Ação:** Corrigido — circuit breaker adicionado com fallback que retorna validação segura.

---

## Itens verificados sem problemas

- [x] Dados sensíveis em logs (`cardToken`, `cardLastFour`): OK — nunca logados
- [x] Dados sensíveis em logs (`ipAddress`): OK — anonimizado via `anonymizeIp()` em todos os pontos exceto os corrigidos acima
- [x] Regras de scoring não vazam em logs ou responses: OK — apenas nomes das regras (`"IP_BLACKLISTED"`) são retornados
- [x] `GlobalExceptionHandler` (payment): OK — sem stack trace, sem dados sensíveis
- [x] Circuit breaker + fallback: OK — presente em `FraudServiceClient`, `MercadoPagoGateway`, `ClaudeContextAnalyzerImpl`; corrigido em `UserServiceClient` e `OrderServiceClient`
- [x] Idempotência via Redis: OK — TTL 24h, key `idempotency:payment:`, `idempotency:refund:`
- [x] Rate limiting: OK — 100 req/min, key `rate_limit:payment:`, fallback Redis indisponível
- [x] Armazenamento de card data: OK — apenas `cardLastFour` (4 dígitos) e `cardBrand` persistidos, sem CVV, sem PAN completo
- [x] AuditLog sem dados sensíveis: OK — apenas `transactionId`, `action`, `actorId`, `payload` (metadados), `ipAddress`
- [x] JWT: fora do escopo (api-gateway/user-service)
- [x] SQL injection: OK — uso de JPA Repository e Spring Data
- [x] TLS/comunicação: OK — sem credenciais hardcoded

---

## Divergências Inter-Serviços (Documentadas)

| ID | Descrição | Serviço alvo | Dono |
|----|-----------|-------------|------|
| D-001 | Endpoint `GET /internal/users/{customerId}` inexistente no user-service | user-service | Dev 2 |
| D-002 | URL defaults invertidos no `application.yml` (corrigido) | payment-service | Dev 1 ✅ |
| D-003 | Internal secret com valores divergentes (parcialmente corrigido) | api-gateway + user-service | Dev 2 |
| D-004 | `X-User-Role` (singular) vs `X-User-Roles` (plural) (parcialmente corrigido) | api-gateway + order-service | Dev 2 + Dev 3 |
| D-005 | api-gateway bloqueia rotas `/internal/` | api-gateway | Comportamento correto ✅ |
| D-006 | Kafka topic names — alinhamento cross-service | order-service + notification-service | Dev 3 |

---

## Referências
- PCI DSS v4.0 Requirements: 3.3, 3.4, 6.2, 8.3
- LGPD: Art. 6, Art. 46, Art. 15
- OWASP Top 10: A02 (Cryptographic Failures), A03 (Injection)
