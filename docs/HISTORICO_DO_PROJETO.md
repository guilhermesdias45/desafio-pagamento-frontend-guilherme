# Histórico do Projeto — Acabou o Mony

> Documento completo de tudo que foi feito no projeto, sprint a sprint, serviço a serviço.
> Última atualização: 2026-06-08

---

## Visão Geral

**Acabou o Mony** é uma fintech de pagamentos digitais voltada para empreendedores e comerciantes. O objetivo central é processar pagamentos com segurança, rapidez e confiabilidade.

- **Repositório:** `https://github.com/Pedro-Luiz-Giraldi/desafio-pagamento`
- **Metodologia:** SDD (Spec-Driven Development) + TDD (Test-Driven Development)
- **Time:** 3 devs (Dev 1, Dev 2, Dev 3)
- **Dev 2 (Lucas):** responsável por `user-service`, `api-gateway` e infraestrutura

---

## Stack Tecnológico

| Camada | Tecnologia |
|--------|-----------|
| Linguagem | Java 21 |
| Framework | Spring Boot 3.x |
| Banco de dados | PostgreSQL 16 |
| Cache | Redis 7 |
| Mensageria | Kafka 3.7 (Confluent) |
| Infra | Docker Compose |
| Monitoramento | New Relic |
| Gateway de pagamento | Mercado Pago SDK Java 2.1.25 |
| Tokenização de cartão | Stripe |
| Build | Maven |
| Testes | JUnit 5, Mockito, Testcontainers |
| Testes de carga | k6 |
| E-mail transacional | Brevo (SMTP) |

---

## Arquitetura de Microserviços

```
Cliente
  └─► api-gateway :8080
        ├─► user-service        :8081   (auth, JWT, 2FA, usuários)
        ├─► payment-service     :8082   (transações, Mercado Pago)
        ├─► order-service       :8083   (pedidos, carrinho)
        ├─► notification-service:8084   (e-mails, Kafka consumers)
        └─► fraud-service       :8085   (análise de risco, regras)
```

Toda comunicação entre serviços usa `X-Internal-Secret` como autenticação interna. O `api-gateway` injeta headers `X-User-Id`, `X-User-Email`, `X-User-Role` e `X-Merchant-Id` em cada requisição autenticada.

---

## Decisões de Arquitetura

| Decisão | Escolha | Motivo |
|---------|---------|--------|
| Gateway de pagamento | Mercado Pago | Melhor cobertura no Brasil |
| Monitoramento | New Relic | Sem Prometheus (complexidade de infra) |
| Infra | Docker Compose | Sem Kubernetes (overhead desnecessário) |
| Kafka | Confluent KRaft | Sem Zookeeper (topologia simplificada) |
| fraud-service | Regras determinísticas | Sem LLM — score 70–89 vai para fila humana |
| Integrações externas | Fora do escopo | TikTok Shop, Instagram, WhatsApp não serão implementados |

---

## Sprint 1 — Fundação e Infraestrutura

**Período:** 2026-05-26 a 2026-05-29  
**Commits relevantes:** `7e94fe8`, `32e93cd`, `8de80cf`, `296d414`, `69377f0`

### O que foi feito

#### Infraestrutura Docker Compose

Criado `docker-compose.yml` completo com todos os 9 containers:

- **Infraestrutura:**
  - `aom-postgres` — PostgreSQL 16-alpine, porta 5432
  - `aom-redis` — Redis 7-alpine, porta 6379
  - `aom-kafka` — Confluent cp-kafka 7.5.0 KRaft mode, porta 9094
  - `aom-kafka-init` — container one-shot que cria os 10 tópicos Kafka no boot

- **Serviços (profile `app`):**
  - `aom-user-service` :8081
  - `aom-api-gateway` :8080
  - `aom-payment-service` :8082
  - `aom-order-service` :8083
  - `aom-notification-service` :8084
  - `aom-fraud-service` :8085

#### Scripts e Configuração

- `scripts/init-databases.sql` — cria os 5 bancos isolados por serviço
- `.env.example` — todas as variáveis documentadas com instruções
- `TEAM.md` — divisão de responsabilidades entre os 3 devs
- `README.md` — guia de setup local

#### Estrutura Maven dos 6 Serviços

Cada serviço recebeu:
- `pom.xml` com dependências específicas (Spring Boot, Testcontainers, JaCoCo ≥ 90%)
- `Dockerfile` multi-stage com usuário não-root
- `application.yml` + `application-docker.yml`
- Estrutura de pastas: `controller/`, `service/`, `repository/`, `domain/`, `dto/`, `exception/`, `config/`, `mapper/`

#### Specs SDD

Criados arquivos de especificação em `specs/` para todos os 6 serviços:
- `spec.md` — especificação detalhada do serviço
- `plan.md` — plano de implementação
- `tasks.md` — tasks ordenadas

---

### user-service — Sprint 1 Completo

**Commit:** `32e93cd` → `8de80cf` → `296d414` (145 arquivos, 65 testes, JaCoCo 90.5%)

#### Domínio

- **`User`** — entidade JPA com campos: `id`, `name`, `email` (único), `passwordHash`, `role` (`CUSTOMER` / `MERCHANT_OWNER` / `STAFF`), `status` (`PENDING_VERIFICATION` / `ACTIVE` / `BLOCKED`)
- **`Merchant`** — entidade vinculada a `User` com `businessName`, `cnpj`, `status`
- **`RefreshToken`** — entidade para gerenciar sessões múltiplas
- **`RecoveryCode`** — 8 códigos de recuperação de 2FA por usuário
- **`AuditLog`** — registro imutável de todos os eventos de segurança
- Flyway migrations V1–V5 para criação e evolução do schema

#### Autenticação e Segurança

- **Registro** — `POST /api/v1/auth/register` — cria usuário PENDING, envia e-mail de confirmação com token
- **Confirmação de e-mail** — `POST /api/v1/auth/confirm-email` — ativa conta, transição PENDING → ACTIVE
- **Login** — `POST /api/v1/auth/login` — valida credenciais, retorna JWT RS256 (15 min) + cookie HttpOnly com refresh token
- **Refresh** — `POST /api/v1/auth/refresh` — troca refresh token por novo par de tokens
- **Logout** — `POST /api/v1/auth/logout` — revoga refresh token, blacklista access token no Redis
- **Reenvio de confirmação** — `POST /api/v1/auth/resend-confirmation`

#### JWT RS256

- Chave RSA 4096-bit gerada na inicialização do projeto
- `JwtTokenProvider` — emite tokens com claims: `userId`, `email`, `role`, `merchantId`, `issuedAt`, `expiresAt`
- `JwtTokenValidator` — valida assinatura e expiração; lança `JwtValidationException` tipada
- `RsaKeyLoader` — aceita PKCS#1 e PKCS#8 (correção aplicada no `fix(user-service)`)

#### 2FA (TOTP)

- `TwoFactorService` — setup TOTP com QR code, verificação de código, armazenamento seguro de secret no Redis durante setup
- 8 recovery codes gerados no setup, armazenados hasheados, uso único
- Flow: `POST /api/v1/auth/2fa/setup` → QR code → `POST /api/v1/auth/2fa/confirm` → 2FA ativo

#### Proteção contra Força Bruta

- `LoginAttemptService` — 5 tentativas falhas → bloqueio de 30 minutos via Redis
- Chave Redis: `hash SHA-256 do e-mail` (não PII exposto em cache) — corrigido em bug fix de Jun/5

#### Endpoints Internos

- `POST /internal/auth/validate-token` — valida Bearer token e retorna claims JSON para o api-gateway
- `GET /internal/users/{userId}` — retorna dados do usuário para outros serviços; rejeita contas não-ACTIVE com 404
- `InternalSecretFilter` — valida header `X-Internal-Secret` em todas as rotas `/internal/`

#### Kafka

Eventos publicados para tópicos individuais (corrigido em bug fix de Jun/5):
- `user.registered` → `UserRegisteredEvent` (com token de confirmação para notification-service)
- `user.login.success` → evento de login
- `user.login.blocked` → alerta de bloqueio
- `user.2fa.enabled` → notificação de ativação

---

## Sprint 2 — api-gateway

**Período:** 2026-06-01 a 2026-06-02  
**Commits relevantes:** `0e01332`, `f3e1186`, `7533a16`, `448429d`

### api-gateway — Sprint 2 Completo

**39 testes passando, JaCoCo 91%**

#### Filtros (em ordem de execução)

| Filtro | Order | Responsabilidade |
|--------|-------|-----------------|
| `CorrelationIdFilter` | -10 | Gera/propaga `X-Correlation-Id` em todas as requisições |
| `AuthenticationFilter` | -5 | Libera `/api/v1/auth/**`, valida Bearer token via user-service, injeta `X-User-*` headers |
| `LoggingFilter` | 1 | Log JSON estruturado: `correlationId`, método, path, status, `durationMs` |
| `SecurityHeadersFilter` | LOWEST | HSTS, `X-Content-Type-Options`, `X-Frame-Options` |

#### Autenticação e Validação

- `UserServiceClient` — chama `POST /internal/auth/validate-token` com cache Redis de 30s (`token_validation:<sha256>`)
- Token blacklistado no logout invalida o cache imediatamente (integração com `AuthService.logout()`)
- Headers injetados downstream: `X-User-Id`, `X-User-Email`, `X-User-Role`, `X-Merchant-Id`

#### Rate Limiting

- `RequestRateLimiter` com Redis — por IP (default) e por usuário autenticado
- `@Primary` em `ipKeyResolver` para resolver ambiguidade de beans

#### Circuit Breaker

- Resilience4j configurado para chamadas ao `user-service`
- Fallback retorna 503 com mensagem estruturada

#### Roteamento

- Rotas configuradas para todos os 6 serviços com `lb://` (Spring Cloud LoadBalancer)
- `/internal/**` não tem rota — retorna 404 via `GatewayExceptionHandler`

#### Bugs Corrigidos Nessa Fase

1. **KeyResolver ambíguo** — dois beans sem `@Primary` causavam crash loop; corrigido com `@Primary` em `ipKeyResolver`
2. **Enums PostgreSQL** — `@JdbcTypeCode(SqlTypes.NAMED_ENUM)` em `User.role`, `User.status`, `Merchant.status` para evitar `ClassCastException` no Hibernate 6.x
3. **`/internal/**` retornava 503** — `GatewayExceptionHandler` passou a tratar `ResponseStatusException(404)` → 404 correto

---

## Sprint 3 — Serviços de Negócio

**Período:** 2026-05-29 a 2026-06-03  
**Commits relevantes:** `de70b66`, `d324086`, `ec14480`, `f9b71d1`, `505b920`, `420b19f`, `9c432b9`

### payment-service

**177 testes passando**

#### Funcionalidades

- `POST /api/v1/transactions` — processa pagamento via Mercado Pago SDK
- Fluxo completo: Validação → Antifraude (via `FraudServiceClient`) → Mercado Pago → PostgreSQL → Kafka event
- Idempotência via `idempotencyKey` (header `Idempotency-Key`) — chave Redis com TTL de 24h
- Resultado `sealed interface TransactionResult` com `Success` e `Failure`
- `AuditLog` com payload JSONB para rastreabilidade completa
- `GET /api/v1/transactions/{id}` — consulta transação por ID
- `POST /api/v1/transactions/{id}/refund` — estorno

#### Integrações Internas

- `FraudServiceClient` — chama `POST /internal/fraud/score` com `X-Internal-Secret`
- `OrderServiceClient` — chama `GET /internal/orders/{orderId}` com `X-Internal-Secret`
- `UserServiceClient` — resolve e-mail do cliente para eventos Kafka

#### Eventos Kafka Publicados

- `payment.transaction.completed` → `TransactionCompletedEvent`
- `payment.transaction.failed` → `TransactionFailedEvent`
- `payment.transaction.refunded` → `TransactionRefundedEvent`

### fraud-service

**Score 0–100 — acima de 90 bloqueia automaticamente**

#### Regras Determinísticas

- `VelocityCheckRule` — muitas transações em curto período
- `BlacklistRule` — IP ou cartão na blacklist
- `HighValueFirstPurchaseRule` — primeiro pedido acima de threshold
- `NewDeviceHighValueRule` — dispositivo novo + valor alto
- `GeoVelocityRule` — transações impossíveis geograficamente
- `FraudDetectionService` — orquestra regras, calcula score agregado
- `RuleEngineService` — injeta regras via DI, executa em paralelo

#### Faixas de Score

| Score | Decisão |
|-------|---------|
| 0–69 | `APPROVE` |
| 70–89 | `REVIEW` (fila humana) |
| 90–100 | `BLOCK` |

#### Endpoint Interno

- `POST /internal/fraud/score` — recebe `FraudAnalysisRequest` e retorna `FraudScoreResponse`

### order-service

**81 testes passando (2 skipped)**

- CRUD completo de pedidos
- `POST /api/v1/orders` — cria pedido com `Idempotency-Key` obrigatório
- `GET /api/v1/orders/{id}` — consulta pedido
- `PATCH /api/v1/orders/{id}/cancel` — cancela pedido
- Job de expiração automática de pedidos pendentes
- Eventos Kafka: `order.created`, `order.cancelled`
- Endpoint interno: `GET /internal/orders/{orderId}` (sem wrapper `{data:...}`)

### notification-service

**36 testes passando**

- Kafka consumer para eventos de todos os serviços
- `EmailService` — envio via Brevo SMTP com retry (3 tentativas, backoff: 1s / 5s / 30s)
- Templates Thymeleaf para cada tipo de e-mail:
  - Confirmação de e-mail (com link + token)
  - Boas-vindas
  - Transação aprovada
  - Transação recusada
  - Alerta de fraude
  - Bloqueio de login
- `NotificationLog` — persiste histórico de envios no PostgreSQL
- `spring.json.type.mapping` para desserialização correta dos 9 tipos de evento Kafka

---

## Sprint 4 — Integração, Compliance e Hardening

**Período:** 2026-06-03 a 2026-06-05  
**Commits relevantes:** `829d08a`, `47a733a`, `211e0c5`, `db1c689`, `130a94a`, `919790e`, `39114f0`

### Resolução de Divergências de Merge

Ao integrar os branches dos 3 devs (`dev1`, `dev2`, `dev3`) na `main`, foram identificadas e corrigidas divergências:

- **D-001** — formato de `FraudEvent` incompatível entre `payment-service` e `notification-service`
- **D-003** — contrato do `OrderServiceClient` usando endpoint público em vez de `/internal/`
- **D-004** — erros de compilação por imports quebrados após merge

---

### Bug Fixes — 5 de Junho de 2026

Em 2026-06-05 foram realizadas duas rodadas de correção a partir de testes manuais com o sistema rodando em Docker. Total: **8 arquivos alterados em 58 linhas** (commit `39114f0`) + 5 arquivos anteriores.

#### Rodada 1 (commit `47a733a`) — user-service: 6 bugs críticos

| Bug | Arquivo | Descrição |
|-----|---------|-----------|
| 2FA mismatch | `TwoFactorService.java` | Recovery codes gerados no setup mas não armazenados consistentemente; confirm() lia codes diferentes dos exibidos |
| Kafka tópico único | `UserEventProducer.java` | Todos os eventos publicados em `user-events`; notification-service não consumia; corrigido para tópicos individuais |
| Token ausente no evento | `UserRegisteredEvent.java` | Token de confirmação não incluído no evento; notification-service não conseguia montar o link |
| Contas inativas | `InternalUserController.java` | Usuários BLOCKED/PENDING passavam pela validação interna; agora retorna 404 |
| Stub 501 | `AuthController.java` | `resend-confirmation` retornava 501; implementado completamente |
| PII em cache | `LoginAttemptService.java` | Chave Redis usava e-mail em texto claro; corrigido para SHA-256 do e-mail |

#### Rodada 2 (commit `211e0c5`) — fraud-service: 3 bugs + 4 erros de compilação

| Bug | Descrição |
|-----|-----------|
| `ConflictingBeanDefinitionException` | Dois beans `fraudEventProducer` (`@Service` + `@Component`); removido o duplicado |
| Regras não injetadas | `RuleEngineService` não usava DI para `FirstPurchaseMaxValueRule` e `NewDeviceHighValueRule` |
| LGPD — PII em banco | `autoBlacklistIp()` persistia `request.toString()` (com `customerId` + `ipAddress`) no campo `reason`; corrigido para armazenar apenas o score |
| Testes sem `SecurityConfig` | `FraudControllerTest` carregava `InternalSecretFilter` sem mock do secret; corrigido com `@Import` |

#### Rodada 3 (commits `db1c689`, `130a94a`, `919790e`) — payment-service e Docker

| Bug | Arquivo | Descrição |
|-----|---------|-----------|
| Header errado | `TransactionController.java` | Lia `X-Customer-Email` em vez de `X-User-Email` (injetado pelo api-gateway) → 400 em toda transação |
| Endpoint público interno | `OrderServiceClient.java` | Chamava endpoint público com `X-User-Role: ADMIN`; corrigido para `/internal/orders/{id}` com `X-Internal-Secret` |
| `403` no fraud-service | `FraudServiceClient.java` | Faltava header `X-Internal-Secret` nas chamadas; adicionado |
| JSONB inválido | `AuditLog.java` | Faltava `@JdbcTypeCode(SqlTypes.JSON)` no campo `payload`; PostgreSQL rejeitava |
| Endpoint duplicado | `FraudController.java` | Mesmo endpoint que `InternalFraudController`; causava `AmbiguousMappingException`; removido |
| Docker — SMTP health | `application-docker.yml` | `SmtpHealthIndicator` tentava conectar com credenciais placeholder; desabilitado no perfil Docker |

#### Rodada 4 (commit `39114f0`) — Bugs descobertos em testes manuais completos

| Bug | Arquivo | Descrição |
|-----|---------|-----------|
| Resolução DNS Docker | `docker-compose.yml` | `payment-service` não resolvia `user-service:8081`; faltava `USER_SERVICE_URL` no `environment` |
| Logout sem blacklist | `AuthService.java` / `AuthController.java` | Logout só revogava refresh token; access token permanecia válido até expirar; agora blacklistado no Redis com TTL = tempo restante |
| Cache do gateway obsoleto | `AuthService.java` | Token blacklistado continuava válido por 30s (cache do `UserServiceClient`); logout deleta a chave `token_validation:<sha256(token)>` do Redis |
| Token revogado aceito | `InternalAuthController.java` | `validate-token` não verificava o blacklist; agora verifica antes da validação JWT → retorna 401 TOKEN_REVOKED |
| JSONB inválido na transação | `TransactionService.java` | Duas chamadas `logAudit()` gravavam strings simples em coluna JSONB; corrigido para `{"risk":"..."}` e `{"detail":"..."}` |
| Kafka desserialização | `notification-service/application.yml` | `ClassNotFoundException` ao consumir eventos; adicionado `spring.json.type.mapping` com 9 mapeamentos |
| Remetente SMTP | `EmailService.java` | Brevo exige `setFrom()` explícito; adicionado `@Value("${MAIL_FROM}")` |

---

## Validação E2E em Docker

Após todas as correções, o fluxo end-to-end foi validado manualmente:

| Fluxo | Resultado |
|-------|-----------|
| Registro de usuário | ✅ 201 — e-mail de confirmação recebido |
| Confirmação de e-mail via token | ✅ 200 — conta ativada |
| Login com JWT RS256 | ✅ 200 + cookie HttpOnly |
| `GET /users/me` com Bearer token | ✅ 200 |
| Logout com access token + refresh cookie | ✅ 204 — token blacklistado |
| `GET /users/me` após logout | ✅ 401 TOKEN_REVOKED imediato |
| Criar pedido com `Idempotency-Key` | ✅ 201 |
| Processar pagamento | ✅ Chega até o gateway MP (timeout esperado em sandbox) |
| AuditLog JSONB | ✅ JSON válido gravado no PostgreSQL |

---

## Resultado dos Testes por Serviço

| Serviço | Testes | Status | Cobertura |
|---------|--------|--------|-----------|
| user-service | 171 | ✅ BUILD SUCCESS | ≥ 90% |
| api-gateway | 39 | ✅ BUILD SUCCESS | 91% |
| payment-service | 177 | ✅ BUILD SUCCESS | ≥ 90% |
| order-service | 81 (2 skipped) | ✅ BUILD SUCCESS | ≥ 90% |
| notification-service | 36 | ✅ BUILD SUCCESS | ≥ 90% |
| fraud-service | — | fixes aplicados, mvn test pendente | — |

---

## Segurança Implementada

- **PCI DSS Level 1** — nenhum dado de cartão armazenado; apenas últimos 4 dígitos em logs
- **LGPD** — PII nunca exposto em cache Redis, logs ou eventos Kafka
- **JWT RS256** — expiração de 15 minutos; refresh token em cookie HttpOnly
- **TLS 1.3** — obrigatório em todas as chamadas externas
- **2FA TOTP** — QR code + 8 recovery codes hasheados
- **Rate limiting** — por IP e por usuário autenticado via Redis
- **Blacklist de tokens** — access token revogado imediatamente no logout
- **Antifraude ML** — score 0–100; bloqueio automático acima de 90
- **X-Internal-Secret** — autenticação em todas as rotas `/internal/`
- **SHA-256 em cache** — chave de cache de token usa hash, não token raw

---

## Dados de Teste (Ambiente Docker Local)

```
Usuário customer ativo:   test.bugfix@acaboumony.com  /  Test@1234
  userId: 87d31f09-150c-4610-aec4-802571f1c59a

Merchant existente:       ana@teste.com  /  Senha@123
  merchantId: 763ab5c5-fd27-43d9-888c-5f1544c2ab64
  businessName: Roupas da Ana

CNPJs já cadastrados:     11222333000181 | 60701190000104 | 12345678000195
Card token válido:        32 chars hex — regex ^[a-fA-F0-9]{32}$
Tokens JWT expiram em:    900s (15 min)
Idempotency-Key:          UUID obrigatório no header
```

---

## Subir o Ambiente

```bash
# Na raiz do projeto (acabou-o-mony/)
docker compose up --build

# Todos os 9 containers devem ficar healthy:
# aom-postgres, aom-redis, aom-kafka
# aom-user-service, aom-api-gateway, aom-payment-service
# aom-order-service, aom-notification-service, aom-fraud-service
```

---

## O que Ainda Falta (Sprint 4 — Pendente)

- [ ] **fraud-service** — confirmar `mvn test` verde após os 7 fixes aplicados
- [ ] **Observabilidade New Relic** — integrar Micrometer em todos os serviços
- [ ] **Testes de carga k6** — validar P99 < 1s em `/api/v1/transactions`
- [ ] **Token Mercado Pago sandbox** — substituir `TEST-CHANGE_ME` no `.env`
- [ ] **Documentação de API** — atualizar contratos em `docs/architecture/api-contracts.md`

---

## Histórico de Commits Relevantes

| Hash | Descrição |
|------|-----------|
| `7e94fe8` | chore: initial project setup |
| `32e93cd` | feat(user-service): Sprint 1 completo — auth, JWT, 2FA, refresh |
| `8de80cf` | test(user-service): cobertura sobe para 90.5% |
| `0e01332` | feat(api-gateway): Sprint 2 completo |
| `f3e1186` | fix(user-service): RsaKeyLoader aceita PKCS#1 e PKCS#8 |
| `448429d` | fix(user-service, api-gateway): enums PostgreSQL + handler 404 |
| `de70b66` | feat: Sprint 1 — payment, fraud, order (Dev 1 + Dev 3) |
| `829d08a` | fix(inter-service): divergências de merge D-001, D-003, D-004 |
| `47a733a` | fix(user-service): 6 bugs críticos de segurança |
| `211e0c5` | fix(fraud-service): 3 bugs + 4 erros de compilação |
| `db1c689` | fix(payment-service): header X-User-Email + endpoint interno |
| `130a94a` | fix(fraud-service, notification-service): Docker startup failures |
| `919790e` | fix(payment-service): X-Internal-Secret, AuditLog JSONB, k6 |
| `39114f0` | fix: 7 bugs manuais — logout blacklist, DNS Docker, JSONB, Kafka |
