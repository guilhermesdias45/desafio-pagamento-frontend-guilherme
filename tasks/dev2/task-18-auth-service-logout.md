# Task 18: AuthService.logout() — invalidação de refresh token

## Objective
Implementar `AuthService.logout(UUID userId, String refreshToken)` que invalida o refresh token específico no Redis. Access token (JWT) tem TTL curto (15 min) — não é revogado server-side; expira naturalmente. Audit log da ação.

## Context
**Quick Context:**
- POST `/api/v1/auth/logout` — autenticado (JWT necessário). UserId vem do JWT (em `SecurityContext`); refresh token vem do cookie.
- Logout single-token (apenas a sessão atual). Logout "all sessions" (revogar todos refresh tokens do user) é Sprint 2.
- Idempotente: chamar logout 2x para o mesmo token retorna 200 nas duas.

Ler antes:
- `specs/user-service/spec.md` §2 (endpoint `/api/v1/auth/logout`)
- `tasks/dev2/task-15` e `task-17` (RefreshTokenService)

## Target Files
**Modify:**
- `services/user-service/src/main/java/com/acaboumony/user/service/AuthService.java` (adicionar método `logout`)
- `services/user-service/src/main/java/com/acaboumony/user/service/RefreshTokenService.java` (adicionar método `revoke`)

**Test:**
- `services/user-service/src/test/java/com/acaboumony/user/service/AuthServiceLogoutIT.java`

## Dependencies
- Depends on: task-15, task-17
- Blocks: task-20

## TDD Mode

### RED
`AuthServiceLogoutIT extends BaseIntegrationTest`:

Setup: criar User ACTIVE + obter refreshToken via authenticate().

- `deve_deletar_refresh_token_do_redis_quando_logout()`.
- `deve_ser_idempotente_quando_logout_chamado_2x_com_mesmo_token()` — sem exception, sem-op na 2ª chamada.
- `deve_audit_log_LOGOUT_event_quando_logout()`.
- `deve_NAO_afetar_outros_refresh_tokens_do_mesmo_user()` — criar 2 sessões, logout 1, verifica que a outra continua válida.

Roda → falha.

### GREEN
1. **`RefreshTokenService.revoke(String token)`** — DEL `refresh_token_lookup:{token}` (key reversa) e DEL `refresh_token:{userId}:{token}` se key reversa existia. Retorna void. Idempotente (DEL inexistente = no-op).
2. **`AuthService.logout(UUID userId, String refreshToken)`**:
   ```java
   refreshTokenService.revoke(refreshToken);
   userAuditLogger.log(userId, "LOGOUT", null, null);
   ```

### REFACTOR
- Considerar incluir o userId na revogação para defesa-em-profundidade (não revogar token de outro user mesmo se key colidir).
- Logging: `logger.info("Logout: userId={}", userId)`.

## Acceptance Criteria
- [ ] `AuthServiceLogoutIT` passa (4 testes)
- [ ] Refresh token deletado do Redis após logout
- [ ] Logout idempotente
- [ ] Audit log entry com `event_type = "LOGOUT"`
- [ ] Outros refresh tokens do mesmo user NÃO são afetados
