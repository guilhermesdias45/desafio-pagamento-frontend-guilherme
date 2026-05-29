# Sprint 1 — Relatório de Progresso

**Período:** Sprint 1
**Status:** Infraestrutura e scaffolding concluídos — implementação pendente

---

## O que foi feito

### Infraestrutura (Dev 2) ✅

| Artefato | Descrição |
|----------|-----------|
| `docker-compose.yml` | PostgreSQL 16, Redis 7, Kafka 3.7 (KRaft), kafka-init, todos os 6 serviços declarados com profiles |
| `scripts/init-databases.sql` | Cria os 5 bancos isolados (`user_db`, `payment_db`, `order_db`, `notification_db`, `fraud_db`) na primeira execução |
| `.env.example` | Todas as variáveis documentadas: Postgres, Redis, Kafka, JWT RS256, AES-256, Mercado Pago, SMTP, New Relic, Anthropic |

**Decisões de arquitetura tomadas:**
- Kafka em modo KRaft (sem Zookeeper) — Kafka 3.7 suporta nativamente
- Serviços de app em `profile: app` — `docker compose up` sobe só a infra
- `fraud-service` sem porta exposta — apenas interno via rede Docker
- Listener duplo no Kafka: `kafka:9092` (containers) e `localhost:9094` (IDE local)

---

### Estrutura dos Microserviços ✅

Todos os 6 serviços criados em `services/` com estrutura Maven completa:

| Serviço | Dev | Porta | pom.xml | Dockerfile | application.yml | Application.java |
|---------|-----|-------|---------|------------|-----------------|-----------------|
| `user-service` | Dev 2 | 8081 | ✅ | ✅ | ✅ | ✅ |
| `api-gateway` | Dev 2 | 8080 | ✅ | ✅ | ✅ | ✅ |
| `payment-service` | Dev 1 | 8082 | ✅ | ✅ | ✅ | ✅ |
| `fraud-service` | Dev 1 | 8085 | ✅ | ✅ | ✅ | ✅ |
| `order-service` | Dev 3 | 8083 | ✅ | ✅ | ✅ | ✅ |
| `notification-service` | Dev 3 | 8084 | ✅ | ✅ | ✅ | ✅ |

**Padrões aplicados em todos os serviços:**
- Spring Boot 3.4.5 + Java 21
- Virtual Threads habilitados (`spring.threads.virtual.enabled: true`)
- Dockerfile multi-stage com usuário não-root (PCI DSS)
- `build.finalName = app` (JAR sempre chamado `app.jar`)
- JaCoCo configurado com cobertura mínima de 90%
- Testcontainers para testes de integração (sem H2)

---

### Implementação pendente do Sprint 1

> As tarefas abaixo fazem parte do Sprint 1 mas ainda não foram implementadas.
> A implementação segue o fluxo SDD + TDD: spec já existe em `specs/` — escrever testes antes do código.

#### Dev 2
- [ ] `user-service`: entidades de domínio (`User`, `RecoveryCode`)
- [ ] `user-service`: Flyway migration V1 (`users`, `recovery_codes`)
- [ ] `user-service`: `POST /api/v1/auth/register`
- [ ] `user-service`: `POST /api/v1/auth/login` com JWT RS256
- [ ] `user-service`: `POST /api/v1/auth/refresh`
- [ ] `user-service`: `POST /api/v1/auth/logout`
- [ ] `user-service`: `POST /internal/auth/validate-token`
- [ ] `user-service`: rate limiting (5 tentativas → bloqueio 30 min via Redis)
- [ ] `api-gateway`: `AuthenticationFilter` (chama `/internal/auth/validate-token`)
- [ ] `api-gateway`: injeção de headers `X-User-Id`, `X-User-Email`, `X-User-Roles`, `X-Merchant-Id`, `X-Correlation-Id`
- [ ] `api-gateway`: `RequestRateLimiter` com Redis (rotas públicas e autenticadas)

#### Dev 1
- [ ] `fraud-service`: todas as regras determinísticas (9 regras de scoring)
- [ ] `fraud-service`: velocity checks via Redis
- [ ] `fraud-service`: blacklist de IPs no Redis
- [ ] `fraud-service`: Flyway migration V1 (`fraud_alerts`)
- [ ] `payment-service`: `POST /api/v1/transactions` com Mercado Pago sandbox
- [ ] `payment-service`: Flyway migration V1 (`transactions`)

#### Dev 3
- [ ] `order-service`: CRUD de pedidos (`POST`, `GET`, `DELETE`)
- [ ] `order-service`: cálculo de total e idempotência
- [ ] `order-service`: Flyway migration V1 (`orders`)

---

## Como subir o ambiente

```bash
# 1. Configurar variáveis
cp .env.example .env
# Editar .env com os valores reais

# 2. Subir infra (Postgres + Redis + Kafka)
docker compose up

# 3. Quando os serviços estiverem implementados
docker compose --profile app up --build
```

---

# Sprint 2 — Planejamento

## Dev 2 (você)

### 2FA completo no user-service
- [ ] `POST /api/v1/auth/2fa/setup` — gerar secret TOTP + QR code + 8 recovery codes
- [ ] `POST /api/v1/auth/2fa/confirm` — ativar 2FA com primeiro code gerado
- [ ] `POST /api/v1/auth/2fa/verify` — verificar code no login
- [ ] `POST /api/v1/auth/2fa/disable` — desativar (requer senha + code atual)
- [ ] `POST /api/v1/auth/2fa/recovery` — login de emergência com recovery code
- [ ] Secret TOTP criptografado com AES-256 em repouso
- [ ] Recovery codes hasheados com BCrypt (8 códigos, cada um de uso único)
- [ ] Evento `user.2fa.enabled` publicado no Kafka

### Circuit Breaker no api-gateway
- [ ] Configurar Resilience4j para cada rota downstream
- [ ] Fallback response quando serviço indisponível (HTTP 503 padronizado)
- [ ] Métricas de circuit breaker expostas no `/actuator/metrics`

---

## Dev 1

### payment-service
- [ ] `GET /api/v1/transactions/{id}` — consultar status da transação
- [ ] `POST /api/v1/transactions/{id}/refund` — estorno parcial e total
- [ ] Idempotência garantida via `idempotencyKey` (Redis + banco)
- [ ] Webhook do Mercado Pago (`POST /webhooks/mercadopago`)
- [ ] Flyway migration V2 (`refunds`)

### fraud-service
- [ ] Integração Claude API para análise contextual (score 70–89)
- [ ] Timeout de 150ms com fallback para score base
- [ ] Evento `fraud.detected` publicado quando BLOCK

---

## Dev 3

### order-service
- [ ] Consumer Kafka: `transaction.completed` → atualizar pedido para `PAID`
- [ ] Job de expiração de pedidos (15 min sem pagamento → `EXPIRED`)
- [ ] Flyway migration V2 (suporte a status `PAID`, `EXPIRED`)

### notification-service
- [ ] Consumer Kafka: `user.registered` → email de boas-vindas
- [ ] Consumer Kafka: `user.login.blocked` → email de alerta de segurança
- [ ] Consumer Kafka: `transaction.completed` → email de confirmação de pagamento
- [ ] Consumer Kafka: `fraud.detected` → email de alerta de fraude
- [ ] Flyway migration V1 (`notification_log`)

---

## Ponto de sincronização obrigatório no Sprint 2

| Quando | O que alinhar |
|--------|--------------|
| Início do Sprint 2 | Dev 2 confirma que `/internal/auth/validate-token` está funcional — Dev 1 e Dev 3 desbloqueados |
| Meio do Sprint 2 | Dev 1 + Dev 3: formato do evento `transaction.completed` no Kafka |
| Final do Sprint 2 | Demo interna: fluxo completo register → login → 2FA → transaction → notification |
