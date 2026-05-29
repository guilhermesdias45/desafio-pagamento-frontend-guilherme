# Task 17: AuthService.refresh() — rotação obrigatória de refresh token

## Objective
Implementar `AuthService.refresh(String refreshToken)` com **rotação obrigatória**: deleta o token antigo do Redis ANTES de retornar o novo. Token reutilizado (já deletado por uma rotação) é rejeitado — invalidando ataques de token roubado.

## Context
**Quick Context:**
- Refresh token = UUID opaque (não JWT). Armazenado em `refresh_token:{userId}:{tokenId}` TTL 7 dias (foi criado em task-15 no login).
- **Invariante crítica**: passo 4 da spec §5.2 — deletar o token antigo do Redis **imediatamente**, antes de gerar o novo. Se o cliente legítimo já renovou, qualquer uso posterior do token antigo (potencialmente roubado) → 401.
- Token vem via `Cookie: refreshToken=<uuid>` no header. Controller (task-20) extrai e passa pro service. Novo token retorna no body como parte de `RefreshResponse` → controller seta cookie.
- Service também regenera o accessToken JWT (usuário pode ter mudado role/merchantId? Em Sprint 1 não, mas é boa prática re-buscar do DB).

Ler antes:
- `specs/user-service/spec.md` §5 (Refresh Token) inteiro
- `tasks/dev2/task-11` (JwtTokenProvider)
- `tasks/dev2/task-15` (refresh token criação no login)
- `tasks/dev2/updated-prd.md` §5 (CE-REFRESH-001)

## Target Files
**Create:**
- `services/user-service/src/main/java/com/acaboumony/user/dto/response/RefreshResponse.java` (Record: accessToken, tokenType, expiresIn, refreshToken)
- `services/user-service/src/main/java/com/acaboumony/user/exception/RefreshTokenInvalidException.java`
- `services/user-service/src/main/java/com/acaboumony/user/service/RefreshTokenService.java` (extraído na task-15 ou criado aqui se não houver)

**Modify:**
- `services/user-service/src/main/java/com/acaboumony/user/service/AuthService.java` (adicionar método `refresh`)

**Test:**
- `services/user-service/src/test/java/com/acaboumony/user/service/AuthServiceRefreshIT.java`

## Dependencies
- Depends on: task-11, task-13, task-15
- Blocks: task-20

## TDD Mode

### RED
`AuthServiceRefreshIT extends BaseIntegrationTest`:

Setup helper: criar User ACTIVE + chamar `authenticate()` para obter um `refreshToken` válido em Redis.

- `deve_retornar_novo_accessToken_e_novo_refreshToken_quando_refresh_com_token_valido()`.
- `deve_deletar_token_antigo_do_Redis_quando_refresh_com_sucesso()` — assert key `refresh_token:{userId}:{oldToken}` não existe após chamada.
- `deve_gravar_novo_refreshToken_em_redis_com_TTL_7_dias()`.
- `deve_rejeitar_quando_token_inexistente_no_redis()` (**CE-REFRESH-001 base case** — token nunca foi criado).
- `deve_rejeitar_quando_token_ja_foi_usado_e_rotacionado()` (**CE-REFRESH-001 core**) — chamar refresh 2x com o mesmo token: 1ª passa, 2ª retorna 401.
- `deve_rejeitar_quando_token_expirou_no_redis()` (simular via property override de TTL ou DEL manual).
- `deve_extrair_userId_corretamente_da_key_refresh_token()` — testa que regenera JWT para o user correto.
- `deve_re_buscar_user_no_DB_quando_refresh()` — se role mudou no DB, JWT novo reflete (futuro-proof).

Roda → falha.

### GREEN
1. **`RefreshTokenService`** (se ainda não criado em task-15):
   ```java
   @Service
   public class RefreshTokenService {
       public String issue(UUID userId) {
           String token = UUID.randomUUID().toString();
           stringRedisTemplate.opsForValue().set(key(userId, token), userId.toString(), Duration.ofDays(7));
           return token;
       }
       public Optional<UUID> validateAndDelete(String token) {
           // Buscar key matching: refresh_token:*:{token}
           Set<String> keys = stringRedisTemplate.keys("refresh_token:*:" + token);
           if (keys.isEmpty()) return Optional.empty();
           String key = keys.iterator().next();
           String userId = stringRedisTemplate.opsForValue().get(key);
           Boolean deleted = stringRedisTemplate.delete(key);
           return Boolean.TRUE.equals(deleted) ? Optional.of(UUID.fromString(userId)) : Optional.empty();
       }
       private String key(UUID userId, String token) { return "refresh_token:" + userId + ":" + token; }
   }
   ```
   > **Nota:** `keys("refresh_token:*:{token}")` em produção é caro (O(n)). Alternativa melhor: armazenar key reversa `refresh_token_lookup:{token} = userId` em paralelo, com mesmo TTL. Use essa abordagem se a wave tiver tempo; senão deixe `keys()` documentado como TODO Sprint 2 para usar SCAN ou key reversa.
2. **`AuthService.refresh(String oldRefreshToken): RefreshResponse`**:
   ```java
   UUID userId = refreshTokenService.validateAndDelete(oldRefreshToken)
       .orElseThrow(() -> new RefreshTokenInvalidException());
   User user = userRepository.findById(userId).orElseThrow(RefreshTokenInvalidException::new);
   String newAccessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getEmail(), user.getRole(),
       user.getMerchant() != null ? user.getMerchant().getId() : null);
   String newRefreshToken = refreshTokenService.issue(user.getId());
   userAuditLogger.log(user.getId(), "REFRESH_SUCCESS", null, null);
   return new RefreshResponse(newAccessToken, "Bearer", 900, newRefreshToken);
   ```
3. **`RefreshResponse` Record** — `(String accessToken, String tokenType, int expiresIn, String refreshToken)`. `refreshToken` aqui é apenas para o controller setar no cookie, NUNCA no body real da resposta REST.

### REFACTOR
- Implementar key reversa `refresh_token_lookup:{token} = userId` em `RefreshTokenService.issue()` e usar GET direto em `validateAndDelete` — O(1) em vez de O(n). Mantém compatibilidade com a key principal para listagem de tokens ativos por user.
- Logging: `logger.info("Refresh token rotated: userId={}", userId)` — nunca logar token.

## Acceptance Criteria
- [ ] `AuthServiceRefreshIT` passa (8 testes), incluindo CE-REFRESH-001
- [ ] Token antigo é DELETADO do Redis antes de retornar o novo (rotação obrigatória)
- [ ] Reuso do mesmo token retorna `RefreshTokenInvalidException` (401)
- [ ] Novo refresh token gravado em `refresh_token:{userId}:{newToken}` TTL 7 dias
- [ ] Novo JWT é re-gerado com claims atualizados do DB
- [ ] `RefreshResponse` é Record com `accessToken`, `tokenType`, `expiresIn=900`, `refreshToken`
- [ ] Logs NUNCA contêm o refresh token
- [ ] Cobertura JaCoCo do `RefreshTokenService` ≥ 95%
