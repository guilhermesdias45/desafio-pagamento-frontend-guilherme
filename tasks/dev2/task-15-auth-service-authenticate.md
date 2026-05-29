# Task 15: AuthService.authenticate() — login com rate limit, lock, 2FA gate, timing attack

## Objective
Implementar `AuthService.authenticate(LoginRequest)` retornando `AuthResult` (sealed: `Success | RequiresTwoFactor | Failure`). Cobre: validação de credenciais com BCrypt constant-time, rate limiting (LoginAttemptService), check de status (ACTIVE / PENDING / LOCKED), gate de 2FA quando ativo, geração de JWT + refresh token, eventos Kafka.

## Context
**Quick Context:**
- `LoginRequest` é Record (em spec.md §4.1): `email`, `password`, `totpCode` (opcional), `deviceFingerprint`.
- Sucesso: gera JWT 15 min (via `JwtTokenProvider` da task-11) + refresh token UUID em Redis `refresh_token:{userId}:{tokenId}` TTL 7 dias. Refresh token retornado via httpOnly cookie (cabeça da resposta — controller decide; service retorna no `AuthResult.Success` campo separado para o controller setar).
- **Timing attack prevention**: SEMPRE rodar BCrypt comparison, mesmo se email não existe (usar BCrypt hash dummy fixo). Tempo de resposta uniforme.
- **2FA gate**: se `user.totpEnabled == true` e `totpCode == null` → retorna `RequiresTwoFactor` com `twoFactorToken` (UUID gravado em Redis `2fa_login:{twoFactorToken}` = userId, TTL 5 min). Cliente faz POST /2fa/verify com esse token.
- Mensagens de erro genéricas (sem revelar qual campo falhou) — CE-LOGIN-003.

Ler antes:
- `specs/user-service/spec.md` §4 inteiro (Autenticar Usuário)
- `tasks/dev2/task-08` (User entity, status enum)
- `tasks/dev2/task-11` (JwtTokenProvider)
- `tasks/dev2/task-12` (UserEventProducer)
- `tasks/dev2/task-13` (LoginAttemptService)
- `tasks/dev2/updated-prd.md` §5 (CE-LOGIN-001..004)

## Target Files
**Create:**
- `services/user-service/src/main/java/com/acaboumony/user/dto/request/LoginRequest.java`
- `services/user-service/src/main/java/com/acaboumony/user/result/AuthResult.java` (sealed interface)
- `services/user-service/src/main/java/com/acaboumony/user/exception/InvalidCredentialsException.java`
- `services/user-service/src/main/java/com/acaboumony/user/exception/AccountLockedException.java`
- `services/user-service/src/main/java/com/acaboumony/user/exception/AccountNotConfirmedException.java`
- `services/user-service/src/main/java/com/acaboumony/user/service/UserAuditLogger.java` (componente injetável)

**Modify:**
- `services/user-service/src/main/java/com/acaboumony/user/service/AuthService.java` (adicionar método `authenticate`)

**Test:**
- `services/user-service/src/test/java/com/acaboumony/user/service/AuthServiceAuthenticateIT.java`

## Dependencies
- Depends on: task-08, task-11, task-12, task-13, task-14
- Blocks: task-17, task-19, task-20

## TDD Mode

### RED
`AuthServiceAuthenticateIT extends BaseIntegrationTest`:

Setup helper: persistir um usuário ACTIVE com BCrypt-hashed password "Strong@Pass123".

- `deve_retornar_Success_com_accessToken_e_refreshToken_quando_credenciais_validas_e_status_ACTIVE()`.
- `deve_retornar_Failure_INVALID_CREDENTIALS_quando_email_inexistente()` — sem revelar (mesma mensagem).
- `deve_retornar_Failure_INVALID_CREDENTIALS_quando_senha_errada()`.
- `deve_retornar_Failure_ACCOUNT_NOT_CONFIRMED_quando_user_PENDING_EMAIL_CONFIRMATION()` (**CE-LOGIN-003**).
- `deve_retornar_Failure_ACCOUNT_LOCKED_com_unlockAt_quando_conta_bloqueada_no_Redis()` (**CE-LOGIN-002**).
- `deve_bloquear_conta_e_publicar_user_login_blocked_na_5_tentativa_falha()` (**CE-LOGIN-001**).
- `deve_retornar_RequiresTwoFactor_com_twoFactorToken_quando_2FA_ativo_e_totpCode_ausente()` (**CE-LOGIN-004**).
- `deve_retornar_Success_quando_2FA_ativo_e_totpCode_valido()` (depende de TwoFactorService mock ou real — usar mock para isolar; full integration em task-24).
- `deve_zerar_contador_de_tentativas_quando_login_sucesso()`.
- `deve_publicar_user_login_success_no_Kafka_quando_credenciais_validas()`.
- `deve_ter_tempo_de_resposta_similar_quando_email_existe_vs_nao_existe()` — teste com tolerância (ex: variação < 50ms para 100 chamadas em average) — flaky; aceitável marcar como `@Tag("timing")` e rodar separadamente.
- `deve_armazenar_refresh_token_em_redis_com_TTL_7_dias_quando_Success()`.

Roda → falha.

### GREEN
1. **`LoginRequest`** Record com Bean Validation conforme spec.md §4.1.
2. **`AuthResult` sealed interface** conforme spec.md §4.2:
   ```java
   public sealed interface AuthResult
       permits AuthResult.Success, AuthResult.RequiresTwoFactor, AuthResult.Failure {
       record Success(String accessToken, String tokenType, int expiresIn,
                      boolean requiresTwoFactor, String refreshToken) implements AuthResult {}
       record RequiresTwoFactor(boolean requiresTwoFactor, String twoFactorToken) implements AuthResult {}
       record Failure(String errorCode, String message, boolean retryable, Instant unlockAt) implements AuthResult {}
   }
   ```
   > `refreshToken` no Success é apenas para o controller setar no cookie — nunca retornado no body para o cliente.
3. **`AuthService.authenticate(LoginRequest req): AuthResult`**:
   ```java
   public AuthResult authenticate(LoginRequest req) {
       // 1. Rate limit / locked check
       if (loginAttemptService.isLocked(req.email())) {
           Instant unlockAt = loginAttemptService.getUnlockAt(req.email()).orElse(null);
           return new AuthResult.Failure("ACCOUNT_LOCKED", "Account temporarily locked", false, unlockAt);
       }
       // 2. Busca user
       User user = userRepository.findByEmail(req.email()).orElse(null);
       // 3. Constant-time BCrypt — sempre executa
       String hash = user != null ? user.getPasswordHash() : DUMMY_BCRYPT_HASH;
       boolean passwordOk = bcrypt.matches(req.password(), hash);
       if (user == null || !passwordOk) {
           LoginAttemptResult r = loginAttemptService.recordFailure(req.email());
           if (r.nowLocked()) {
               userEventProducer.publishLoginBlocked(user != null ? user.getId() : null, req.email(), r.unlockAt());
           }
           return new AuthResult.Failure("INVALID_CREDENTIALS", "Invalid email or password", false, null);
       }
       // 4. Status check
       if (user.getStatus() == UserStatus.PENDING_EMAIL_CONFIRMATION) {
           return new AuthResult.Failure("ACCOUNT_NOT_CONFIRMED", "Email not confirmed", false, null);
       }
       if (user.getStatus() == UserStatus.DISABLED) {
           return new AuthResult.Failure("ACCOUNT_DISABLED", "Account disabled", false, null);
       }
       // 5. 2FA gate
       if (user.isTotpEnabled() && (req.totpCode() == null || req.totpCode().isBlank())) {
           String twoFactorToken = UUID.randomUUID().toString();
           stringRedisTemplate.opsForValue().set("2fa_login:" + twoFactorToken, user.getId().toString(), Duration.ofMinutes(5));
           return new AuthResult.RequiresTwoFactor(true, twoFactorToken);
       }
       if (user.isTotpEnabled()) {
           if (!twoFactorService.verifyTotp(user, req.totpCode())) {
               return new AuthResult.Failure("INVALID_TOTP_CODE", "Invalid 2FA code", false, null);
           }
       }
       // 6. Success: gerar tokens
       String accessToken = jwtTokenProvider.generateAccessToken(
           user.getId(), user.getEmail(), user.getRole(),
           user.getMerchant() != null ? user.getMerchant().getId() : null);
       String refreshToken = UUID.randomUUID().toString();
       stringRedisTemplate.opsForValue().set("refresh_token:" + user.getId() + ":" + refreshToken,
           user.getId().toString(), Duration.ofDays(7));
       loginAttemptService.recordSuccess(req.email());
       userEventProducer.publishLoginSuccess(user.getId(), user.getEmail(), req.deviceFingerprint());
       userAuditLogger.log(user.getId(), "LOGIN_SUCCESS", null, req.deviceFingerprint());
       return new AuthResult.Success(accessToken, "Bearer", 900, false, refreshToken);
   }
   ```
4. **`UserAuditLogger`** — `@Service` simples que persiste `UserAuditLog` via `UserAuditLogRepository`. Métodos: `log(UUID userId, String eventType, String ipAddress, String deviceFingerprint)`. Usado também em task-16 e task-17.
5. **DUMMY_BCRYPT_HASH** — constante com BCrypt hash de uma senha aleatória (gerada uma vez). Garante tempo constante quando user não existe.

### REFACTOR
- Encadear lógica com sealed interface helpers ou pattern matching para reduzir nesting.
- Logging estruturado: `logger.info("Login attempt: userId={}, result={}", user?.id, "SUCCESS"|"FAILURE")` — nunca email plaintext, nunca senha.
- Considerar extrair "geração de refresh token" para `RefreshTokenService` para reúso em task-17. **Recomendado** — criar `RefreshTokenService` nesta task; task-17 só precisa adicionar `rotate(...)`.

## Acceptance Criteria
- [ ] `AuthServiceAuthenticateIT` passa (10+ testes), cobrindo CE-LOGIN-001, CE-LOGIN-002, CE-LOGIN-003, CE-LOGIN-004
- [ ] `AuthResult` é sealed interface com exatamente 3 permits (`Success`, `RequiresTwoFactor`, `Failure`)
- [ ] BCrypt comparison sempre executa (mesmo com email inexistente) — verificado por teste de tempo (com tolerância)
- [ ] Refresh token gravado em `refresh_token:{userId}:{tokenId}` com TTL 7 dias
- [ ] `twoFactorToken` para 2FA gate gravado em `2fa_login:{token}` com TTL 5 min
- [ ] Evento `user.login.success` publicado no Kafka em caso de sucesso
- [ ] Evento `user.login.blocked` publicado quando 5ª tentativa falha
- [ ] `UserAuditLog` persiste eventos LOGIN_SUCCESS / LOGIN_FAILED / ACCOUNT_LOCKED
- [ ] Logs NUNCA contêm `password`, `totpCode`, email plaintext, refresh token
- [ ] Mensagem de erro `INVALID_CREDENTIALS` igual para email inexistente e senha errada
