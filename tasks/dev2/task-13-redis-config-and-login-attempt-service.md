# Task 13: RedisConfig + LoginAttemptService (rate limiting helpers)

## Objective
Configurar `RedisConfig` com `StringRedisTemplate` e implementar `LoginAttemptService` que encapsula a lógica de contador de tentativas falhas, bloqueio temporário e check de status — primitivas que `AuthService.authenticate()` (task-15) vai consumir.

## Context
**Quick Context:**
- Redis keys conforme `plan.md` linhas 107-117:
  - `login_attempts:{email}` — contador, TTL 30 min
  - `account_locked:{email}` — flag de bloqueio com valor = unlockAt ISO, TTL 30 min
- Máximo: 5 tentativas → bloqueia. Configurado em `application.yml`: `security.login.max-attempts: 5`, `security.login.lockout-duration-minutes: 30`.
- Rate limiting por **email**, NÃO por IP (decisão do plan.md).
- Pode rodar em paralelo com task-11 e task-12.

Ler antes:
- `specs/user-service/spec.md` §4.4, §4.6, §4.7 (regras de rate limit e lock)
- `specs/user-service/plan.md` §"Chaves Redis" (linhas 107-117)
- `services/user-service/src/main/resources/application.yml` linhas 33-36 (security.login.*)
- `tasks/dev2/updated-prd.md` §5 (CE-LOGIN-001, CE-LOGIN-002)

## Target Files
**Create:**
- `services/user-service/src/main/java/com/acaboumony/user/config/RedisConfig.java`
- `services/user-service/src/main/java/com/acaboumony/user/config/SecurityLoginProperties.java` (@ConfigurationProperties prefix `security.login`)
- `services/user-service/src/main/java/com/acaboumony/user/service/LoginAttemptService.java`
- `services/user-service/src/test/java/com/acaboumony/user/service/LoginAttemptServiceIT.java`

## Dependencies
- Depends on: task-02, task-10 (BaseIntegrationTest)
- Blocks: task-15

## TDD Mode

### RED
`LoginAttemptServiceIT extends BaseIntegrationTest`:
- `deve_retornar_zero_quando_email_nunca_tentou_login()`.
- `deve_incrementar_contador_quando_recordFailure_chamado()`.
- `deve_bloquear_conta_e_retornar_unlockAt_quando_5_tentativas_falhas()` (**CE-LOGIN-001**).
- `deve_indicar_conta_bloqueada_quando_isLocked_chamado_apos_5_falhas()` (**CE-LOGIN-002**).
- `deve_zerar_contador_quando_recordSuccess_chamado()`.
- `deve_expirar_bloqueio_apos_TTL_30_min_quando_isLocked` (não esperar 30 min — mockar Redis ou usar TTL curto via property override).
- `deve_retornar_unlockAt_em_iso8601_quando_getUnlockAt_em_conta_bloqueada()`.

Roda → falha.

### GREEN
1. **`SecurityLoginProperties` record** — `(int maxAttempts, int lockoutDurationMinutes)`, `@ConfigurationProperties("security.login")`. Registrar em `JwtConfig` (que já tem `@EnableConfigurationProperties` — adicionar `SecurityLoginProperties.class`).
2. **`RedisConfig`** — `@Configuration`. Beans:
   - `StringRedisTemplate stringRedisTemplate(RedisConnectionFactory)` — Spring já fornece se não declarado, mas declarar explicitamente para customizar se preciso.
   - `RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory)` com `GenericJackson2JsonRedisSerializer` para values (para uso em outras tasks como refresh token).
3. **`LoginAttemptService`** — `@Service`, injeta `StringRedisTemplate` e `SecurityLoginProperties`.
   - Constantes: `ATTEMPTS_KEY = "login_attempts:%s"`, `LOCKED_KEY = "account_locked:%s"`.
   - `recordFailure(String email): LoginAttemptResult` — INCR `login_attempts:{email}`, set TTL se primeira tentativa, se >= maxAttempts setar `account_locked:{email} = unlockAt` (now + duration) com TTL = duration. Retorna `LoginAttemptResult(int attempts, boolean nowLocked, Instant unlockAt)`.
   - `recordSuccess(String email)` — DEL `login_attempts:{email}` e DEL `account_locked:{email}`.
   - `isLocked(String email): boolean` — EXISTS `account_locked:{email}`.
   - `getUnlockAt(String email): Optional<Instant>` — GET `account_locked:{email}` parse ISO8601, retorna Optional.

### REFACTOR
- `LoginAttemptResult` como record imutável.
- Operações Redis em pipeline ou Lua script para atomicidade do "INCR + check + SET locked" (evita race condition em alta concorrência). Opcional Sprint 1 — documentar como TODO se não implementar agora.
- Logs: `logger.info("Account locked: userId hash={}, attempts={}", hash(email), attempts)` — nunca logar email plano.

## Acceptance Criteria
- [ ] `LoginAttemptServiceIT` passa (7 testes verdes), incluindo CE-LOGIN-001 e CE-LOGIN-002
- [ ] `RedisConfig` registra `StringRedisTemplate` (e opcional `RedisTemplate<String,Object>` com JSON serializer)
- [ ] `SecurityLoginProperties` lido de `application.yml` (`security.login.max-attempts`, `security.login.lockout-duration-minutes`)
- [ ] `recordFailure` na 5ª chamada (com `max-attempts=5`) seta `account_locked` no Redis com TTL 30 min
- [ ] `recordSuccess` deleta ambas as keys (`login_attempts` e `account_locked`)
- [ ] `getUnlockAt` retorna ISO 8601 parseável como `Instant`
- [ ] Email NUNCA aparece em logs como plaintext (hash ou redação)
- [ ] Cobertura JaCoCo do pacote `service` (apenas esta classe) ≥ 90%
