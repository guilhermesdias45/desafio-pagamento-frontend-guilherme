# Updated PRD: User Service — Sprint 1 (dev2)

> Codebase-aware refinement of `tasks/dev2/prd-from-spec.md`. Resolves all ambiguities surfaced in `tasks/dev2/planning-questions.md` with the user's answers. **TDD mode: ON for all functional tasks** (controller, service, security config, validators — RED → GREEN → REFACTOR within the same task).

---

## 1. State of the codebase (relevant findings)

- `services/user-service/` está como esqueleto: `pom.xml` completo (JJWT 0.12.6 RS256, dev.samstevens.totp 1.7.1, Testcontainers postgres+kafka, Lombok+MapStruct, JaCoCo `minimum=0.90` em `BUNDLE`), `application.yml` com Virtual Threads + Flyway + Redis + propriedades `jwt.*` e `security.login.*`, `application-docker.yml` lendo env vars, Dockerfile multi-stage. **Apenas `UserServiceApplication.java` em código Java.**
- `services/fraud-service/`, `services/order-service/` também são esqueletos. **user-service estabelece convenções para o repo** — não há padrão existente a copiar.
- **Flyway:** configurado mas a pasta `src/main/resources/db/migration/` **não existe**. `ddl-auto: validate` significa que qualquer entidade JPA sem migration quebra o boot.
- **`scripts/init-databases.sql`** já cria `user_db` no PostgreSQL via init script.
- **`docker-compose.yml`** atualmente:
  - `kafka-init` cria **4 tópicos separados** (`user.registered`, `user.login.success`, `user.login.blocked`, `user.2fa.enabled`) — **será alterado** para criar **tópico único `user-events`**.
  - `user-service` recebe `AES_SECRET_KEY` — **será renomeado** para `TOTP_AES_KEY` e ganha `INTERNAL_SECRET` + `TOTP_ISSUER`.
- **`.env.example`** tem `JWT_PRIVATE_KEY`, `JWT_PUBLIC_KEY`, `AES_SECRET_KEY` — **falta** `INTERNAL_SECRET`, `TOTP_ISSUER`; `AES_SECRET_KEY` será renomeado para `TOTP_AES_KEY`.
- **`application-docker.yml`** mapeia `aes.secret-key: ${AES_SECRET_KEY}` — **será atualizado** para `totp.aes-key: ${TOTP_AES_KEY}`, `internal.secret: ${INTERNAL_SECRET}`, `totp.issuer: ${TOTP_ISSUER}`.

## 2. Decisões resolvidas (input do usuário)

| # | Decisão |
|---|---------|
| Q1 | **Tópico único `user-events`**. Wave 1 inclui task para atualizar `kafka-init` e remover os 4 tópicos separados. `UserEventProducer` publica em `user-events` com campo `eventType` no payload. |
| Q2 | **Renomear** `AES_SECRET_KEY` → `TOTP_AES_KEY` em `.env.example`, `docker-compose.yml`, `application-docker.yml`. **Adicionar** `INTERNAL_SECRET` e `TOTP_ISSUER` (default `AcabouoMony`) em ambos `.env.example` e `docker-compose.yml`. |
| Q3 | **Flyway**: V1 cria `users` sem FK para `merchants`; V2 cria `merchants` + `ALTER TABLE users ADD CONSTRAINT fk_merchant` no fim do V2; V3 e V4 podem rodar em paralelo após V2. V1→V2 é serial (mesma wave 1). |
| Q4 | **Uma task por unidade lógica** com TDD completo (RED → GREEN → REFACTOR na MESMA task). Não dividir RED/GREEN em arquivos separados. |
| Q5 | **Testcontainers singleton** via `BaseIntegrationTest.java` abstract class com `@Container static` — todas as classes de integração estendem. |
| Q6 | **`UserController` agrupa GET /me + PATCH /me** na mesma task. |
| Q7 | **Class-level custom validator** `@ValidRegisterRequest` aplicado ao Record `RegisterRequest`. Implementa a regra "se role=MERCHANT_OWNER → companyName e cnpj obrigatórios; se CUSTOMER → ignora; se STAFF → INVALID_ROLE". |
| Q8 | **TDD para TODAS as tasks funcionais** (não só lógica de negócio). Cada task com código Java de runtime tem seção `## TDD Mode` com RED → GREEN → REFACTOR. Migrations Flyway e edits de docker-compose/.env.example não são tasks funcionais — não têm TDD formal, mas têm Acceptance Criteria verificáveis. |

## 3. Objetivo Sprint 1

Implementar o `user-service` completo:
- Registro de CUSTOMER e MERCHANT_OWNER (atômico) com validação condicional de CNPJ
- Login com JWT RS256 (15 min) + refresh token (7 dias UUID opaque) **com rotação obrigatória**
- 2FA TOTP (RFC 6238) com secret AES-256-GCM e 8 recovery codes BCrypt
- Endpoint interno `POST /internal/auth/validate-token` protegido por `X-Internal-Secret`
- Rate limiting via Redis (5 tentativas/30 min)
- Eventos Kafka no tópico único `user-events`
- Migrations Flyway V1→V4 com dependência circular resolvida
- Cobertura ≥ 90% JaCoCo

## 4. Estrutura em waves

Ordem para execução paralela por sub-agents. Tasks na mesma wave podem rodar em paralelo; waves consecutivas são serializadas.

### Wave 1 — Foundation (serial)
- task-01: docker-compose + kafka-init + .env.example (user-events, TOTP_AES_KEY, INTERNAL_SECRET, TOTP_ISSUER)
- task-02: application-docker.yml + chaves RSA + JwtConfig stub (bind das novas envs em propriedades Spring)
- task-03: Flyway V1 (users sem FK merchants)
- task-04: Flyway V2 (merchants + ALTER TABLE users ADD fk_merchant)

### Wave 2 — Domain & infra primitives (paralelo após Wave 1)
- task-05: Flyway V3 (recovery_codes)
- task-06: Flyway V4 (user_audit_logs)
- task-07: Enums (UserRole, UserStatus, MerchantStatus)
- task-08: Entidades JPA (User, Merchant, RecoveryCode) + Repositories
- task-09: CnpjValidator + @Cnpj (Bean Validation custom — Módulo 11) [TDD]
- task-10: BaseIntegrationTest (Testcontainers singleton)

### Wave 3 — Security/event primitives (paralelo após Wave 2)
- task-11: JwtTokenProvider + JwtTokenValidator (RS256) [TDD]
- task-12: UserEventProducer (Kafka `user-events`) [TDD]
- task-13: RedisConfig + LoginAttemptService (rate limiting helpers) [TDD]

### Wave 4 — Core business logic (paralelo após Wave 3)
- task-14: RegisterRequest Record + @ValidRegisterRequest validator + MerchantService + AuthService.register() [TDD]
- task-15: AuthService.authenticate() — rate limiting, locking, timing attack prevention [TDD]
- task-16: TwoFactorService — setup/confirm/verify/disable/recovery, AES-256-GCM, BCrypt [TDD]

### Wave 5 — Token lifecycle & internal endpoint (paralelo após Wave 4)
- task-17: AuthService.refresh() — rotação obrigatória [TDD]
- task-18: AuthService.logout() [TDD]
- task-19: InternalSecretFilter + InternalAuthController (POST /internal/auth/validate-token) [TDD]

### Wave 6 — REST surface (paralelo após Wave 5)
- task-20: AuthController + GlobalExceptionHandler (RFC 7807) [TDD]
- task-21: TwoFactorController [TDD]
- task-22: UserController (GET /me + PATCH /me — fullName only) [TDD]

### Wave 7 — Wire-up & integration (após Wave 6)
- task-23: SecurityConfig (SecurityFilterChain — public, internal com filter, authenticated) [TDD]
- task-24: Testes de integração end-to-end (registro → login → refresh com rotação → logout; 2FA full flow; internal validate-token) [Testcontainers via BaseIntegrationTest]

## 5. Casos extremos cobertos por testes (1 teste por CE no mínimo)

Origem: `specs/user-service/spec.md` §3.6, §4.8, §5, §6, §7 e PRD §"Casos extremos".

| CE | Coberto em task |
|----|-----------------|
| CE-REG-001 Email duplicado → 409 | task-14 |
| CE-REG-002 MERCHANT_OWNER sem cnpj → 400 MISSING_MERCHANT_DATA | task-09 (validator) + task-14 |
| CE-REG-003 CNPJ formato inválido → 400 INVALID_CNPJ | task-09 |
| CE-REG-004 CNPJ dígitos verificadores errados → 400 INVALID_CNPJ | task-09 |
| CE-REG-005 CNPJ já cadastrado → 409 CNPJ_ALREADY_REGISTERED | task-14 |
| CE-REG-006 role=STAFF → 400 INVALID_ROLE | task-09 + task-14 |
| CE-REG-007 Falha ao criar merchant → usuário NÃO criado (atômico) | task-14 |
| CE-LOGIN-001 5ª tentativa falha → bloqueia + Kafka user.login.blocked | task-13 + task-15 |
| CE-LOGIN-002 Login com conta bloqueada → 423 com unlockAt | task-15 |
| CE-LOGIN-003 Email não confirmado → 403 sem revelar senha | task-15 |
| CE-LOGIN-004 2FA ativo sem totpCode → requiresTwoFactor=true | task-15 |
| CE-REFRESH-001 Token já usado (rotação) → 401 | task-17 |
| CE-2FA-001 Todos 8 recovery codes usados → RECOVERY_CODE_EXHAUSTED | task-16 |
| CE-INTERNAL-001 Sem X-Internal-Secret → 403 | task-19 + task-23 |
| CE-INTERNAL-002 X-Internal-Secret inválido → 403 | task-19 + task-23 |

## 6. Restrições técnicas (recap, autoritativo)

- Java 21, Records para todos os DTOs, sealed interfaces para `AuthResult` (`Success | RequiresTwoFactor | Failure`), Virtual Threads para I/O.
- PostgreSQL 16 (Testcontainers nunca H2). Redis para rate limiting, refresh tokens, email confirmation, 2FA setup, 2FA login. Kafka KRaft 3.7.
- JWT: JJWT 0.12.6 RS256. Access token 15 min. Refresh token: UUID opaque (não JWT) no Redis, 7 dias.
- TOTP: dev.samstevens.totp 1.7.1, HMAC-SHA1, period 30s, 6 dígitos, tolerância ±1 janela.
- BCrypt rounds=12 (senha + recovery codes).
- AES-256-GCM (não CBC) para secret TOTP no banco.
- RFC 7807 Problem Details em todos os erros.
- **Logs:** NUNCA `password`, `cnpj`, `totpCode`, `refreshToken`, JWT completo, recovery code em claro, chave AES.
- **Tópico único Kafka:** `user-events` com `eventType` no payload (valores: `user.registered`, `user.login.success`, `user.login.blocked`, `user.2fa.enabled`).
- **JaCoCo ≥ 90%** em `BUNDLE`.

## 7. Endpoints (autoritativo)

```
POST   /api/v1/auth/register              público   201 com RegisterResponse
POST   /api/v1/auth/confirm-email         público   200
POST   /api/v1/auth/login                 público   200 AuthResult (Success|RequiresTwoFactor|Failure)
POST   /api/v1/auth/refresh               público   200 (refreshToken via cookie httpOnly)
POST   /api/v1/auth/logout                autenticado
POST   /api/v1/auth/2fa/setup             autenticado
POST   /api/v1/auth/2fa/confirm           autenticado
POST   /api/v1/auth/2fa/verify            público (usa twoFactorToken)
POST   /api/v1/auth/2fa/disable           autenticado
POST   /api/v1/auth/2fa/recovery          público (usa twoFactorToken)
GET    /api/v1/users/me                   autenticado
PATCH  /api/v1/users/me                   autenticado — apenas fullName
POST   /internal/auth/validate-token      X-Internal-Secret obrigatório
```

> `POST /api/v1/auth/resend-confirmation` — **out of scope Sprint 1**. Stub opcional no AuthController retornando 501 Not Implemented com referência ao Sprint 2.

## 8. Variáveis de ambiente (estado final pós-Wave 1)

| Var | Onde | Notas |
|-----|------|-------|
| `JWT_PRIVATE_KEY` | `.env.example`, `docker-compose.yml` (já existe) | RSA 2048 PEM base64, valor de dev fixo |
| `JWT_PUBLIC_KEY` | `.env.example`, `docker-compose.yml` (já existe) | RSA 2048 PEM base64, valor de dev fixo |
| `TOTP_AES_KEY` | `.env.example`, `docker-compose.yml` (**rename** de AES_SECRET_KEY) | 32 bytes hex (64 chars) |
| `TOTP_ISSUER` | `.env.example`, `docker-compose.yml` (**novo**) | Default `AcabouoMony` |
| `INTERNAL_SECRET` | `.env.example`, `docker-compose.yml` (**novo**) | Segredo compartilhado com api-gateway |

## 9. Performance targets (recap)

| Op | P50 | P99 |
|----|-----|-----|
| Register CUSTOMER | 80ms | 200ms |
| Register MERCHANT_OWNER | 100ms | 250ms |
| Login (BCrypt rounds=12) | 150ms | 300ms |
| 2FA verify | 30ms | 100ms |
| Token validate (`/internal`) | 10ms | 50ms |
| Refresh token | 20ms | 80ms |

Não há tarefa explícita de teste de carga no Sprint 1 — performance é monitorada via métricas Actuator em staging.

## 10. Definition of Done — Sprint 1 (Updated PRD)

- [ ] Migrations V1→V4 aplicam contra Testcontainers PostgreSQL sem erros
- [ ] Registro CUSTOMER e MERCHANT_OWNER atômico, com CE-REG-001..007 cobertos
- [ ] Login com rate limit 5/30min, timing attack prevention, CE-LOGIN-001..004 cobertos
- [ ] Refresh token com rotação obrigatória, CE-REFRESH-001 coberto
- [ ] 2FA completo (setup/confirm/verify/disable/recovery), AES-256-GCM, BCrypt para recovery codes, CE-2FA-001 coberto
- [ ] `POST /internal/auth/validate-token` protegido por `X-Internal-Secret`, CE-INTERNAL-001/002 cobertos
- [ ] Tópico Kafka único `user-events` criado no docker-compose; UserEventProducer publica os 4 tipos com `eventType` no payload
- [ ] `.env.example` e `docker-compose.yml` com `TOTP_AES_KEY`, `TOTP_ISSUER`, `INTERNAL_SECRET`
- [ ] Spring Security 6 configurado: rotas públicas, internas (com filter), autenticadas
- [ ] Erros em RFC 7807 via GlobalExceptionHandler
- [ ] `password`, `cnpj`, `totpCode`, `refreshToken`, JWT completo, recovery code nunca em logs
- [ ] Testes de integração via BaseIntegrationTest (Testcontainers singleton): registro → confirm → login → refresh com rotação → logout
- [ ] Cobertura ≥ 90% JaCoCo no build (`./mvnw verify` passa)

## 11. Open Questions

Itens identificados no self-check (Phase 4) que requerem decisão humana antes ou durante a execução:

- **Confirmação de email (POST /api/v1/auth/confirm-email):** o spec.md lista esse endpoint público e o plan.md menciona token UUID no Redis com TTL 24h (`email_confirm:{token}`), porém **a emissão do email é responsabilidade do notification-service** (consome `user.registered` do Kafka). O fluxo de **consumir o token e marcar `status = ACTIVE`** precisa estar implementado no Sprint 1 ou apenas o endpoint stub? **Recomendação:** implementar como parte de task-20 (AuthController) — endpoint público que consome `email_confirm:{token}` do Redis, marca status ACTIVE no banco, e deleta a key. CE não estava no PRD original mas é necessário para o fluxo de login (CE-LOGIN-003 só faz sentido se houver caminho de ACTIVE). **Pergunta para o usuário:** confirmar inclusão em task-20 ou mover para Sprint 2 (e ajustar testes para considerar usuários sempre ACTIVE em dev)?
- **`POST /api/v1/auth/resend-confirmation`:** marcado como Sprint 2 no spec/plan, mas o PRD original o listava como "stub" no AuthController. **Recomendação:** retornar `501 Not Implemented` com referência clara ao Sprint 2 (incluído em task-20). Confirmar com PO se isso é aceitável vs deixar de fora completamente.
- **Auditoria (user_audit_logs):** task-06 cria a tabela (V4) mas nenhuma task funcional escreve nela. O spec.md menciona `LOGIN_SUCCESS`, `LOGIN_FAILED`, `ACCOUNT_LOCKED`, `2FA_ENABLED` como event types. **Recomendação:** adicionar `UserAuditLogger` chamado em AuthService.authenticate() (task-15) e TwoFactorService (task-16). Atualmente esses dois tasks têm escopo grande; preciso confirmar com o usuário se devo expandir o escopo ou criar uma task separada wave 5 (`task-XX: UserAuditLogger`) com TDD próprio. **Default escolhido:** delegar a uma `UserAuditLogger` injetável e incluí-la implicitamente em task-15 e task-16; se ficar grande demais, dev pode criar PR follow-up.
