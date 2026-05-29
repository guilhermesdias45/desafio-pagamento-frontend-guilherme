# Task 19: InternalSecretFilter + POST /internal/auth/validate-token

## Objective
Implementar:
1. `InternalSecretFilter` — Servlet filter que valida header `X-Internal-Secret` em rotas `/internal/**`. Sem header ou valor inválido → 403 imediato (não processa o token).
2. `InternalAuthController` — endpoint `POST /internal/auth/validate-token` que valida JWT RS256 e retorna `{ userId, email, role, merchantId }`.

## Context
**Quick Context:**
- Endpoint **crítico** — está no caminho de toda requisição autenticada do api-gateway. P99 < 50ms.
- `INTERNAL_SECRET` lido de `InternalSecretProperties` (task-02), valor compartilhado com api-gateway.
- O filter precisa ser registrado no `SecurityFilterChain` (task-23) **antes** de qualquer filtro de autenticação JWT — `/internal/**` é um caminho separado que não usa JWT do user, usa o segredo de máquina-a-máquina.
- Validação do JWT em si delega para `JwtTokenValidator` (task-11).

Ler antes:
- `specs/user-service/spec.md` §7 inteiro (Validação de Token Interno)
- `tasks/dev2/task-02` (InternalSecretProperties)
- `tasks/dev2/task-11` (JwtTokenValidator)
- `tasks/dev2/updated-prd.md` §5 (CE-INTERNAL-001, CE-INTERNAL-002)

## Target Files
**Create:**
- `services/user-service/src/main/java/com/acaboumony/user/security/InternalSecretFilter.java`
- `services/user-service/src/main/java/com/acaboumony/user/controller/InternalAuthController.java`
- `services/user-service/src/main/java/com/acaboumony/user/dto/request/ValidateTokenRequest.java` (Record opcional — pode usar `Authorization` header em vez de body)
- `services/user-service/src/main/java/com/acaboumony/user/dto/response/ValidateTokenResponse.java` (Record: userId, email, role, merchantId)
- `services/user-service/src/test/java/com/acaboumony/user/security/InternalSecretFilterTest.java`
- `services/user-service/src/test/java/com/acaboumony/user/controller/InternalAuthControllerIT.java`

## Dependencies
- Depends on: task-02, task-11
- Blocks: task-23

## TDD Mode

### RED
**`InternalSecretFilterTest`** (unitário com `MockMvc` standalone ou Mockito puro):
- `deve_passar_quando_X_Internal_Secret_correto_em_rota_internal()`.
- `deve_retornar_403_quando_X_Internal_Secret_ausente_em_rota_internal()` (**CE-INTERNAL-001**).
- `deve_retornar_403_quando_X_Internal_Secret_invalido_em_rota_internal()` (**CE-INTERNAL-002**).
- `deve_ignorar_filter_quando_rota_nao_e_internal()` (ex: `/api/v1/...` passa direto).
- `deve_comparar_em_constant_time_para_evitar_timing_attack()` — usar `MessageDigest.isEqual` ou `Arrays.equals` de byte[]. Documentar decisão.

**`InternalAuthControllerIT extends BaseIntegrationTest`** (full Spring com SecurityConfig — pode atrasar a task-23 wire-up, mas usar `@TestConfiguration` se necessário para isolar). **Alternativa**: usar `@WebMvcTest(InternalAuthController.class)` + `@Import(InternalSecretFilter.class)` para testar sem Security full.

- `deve_retornar_200_com_userId_email_role_merchantId_quando_JWT_valido_e_header_correto()`.
- `deve_retornar_401_quando_JWT_expirado()`.
- `deve_retornar_401_quando_JWT_assinatura_invalida()`.
- `deve_retornar_403_quando_header_internal_secret_ausente()` (**CE-INTERNAL-001**).
- `deve_retornar_403_quando_header_internal_secret_invalido()` (**CE-INTERNAL-002**).

Roda → falha.

### GREEN
1. **`InternalSecretFilter`** — `extends OncePerRequestFilter`:
   ```java
   protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) {
       if (!req.getRequestURI().startsWith("/internal/")) {
           chain.doFilter(req, res); return;
       }
       String provided = req.getHeader("X-Internal-Secret");
       byte[] expected = internalSecretProperties.secret().getBytes(StandardCharsets.UTF_8);
       if (provided == null || !MessageDigest.isEqual(expected, provided.getBytes(StandardCharsets.UTF_8))) {
           res.setStatus(HttpStatus.FORBIDDEN.value());
           res.setContentType("application/problem+json");
           res.getWriter().write("{\"type\":\"about:blank\",\"title\":\"Forbidden\",\"status\":403,\"detail\":\"Invalid or missing X-Internal-Secret\"}");
           return;
       }
       chain.doFilter(req, res);
   }
   ```
2. **`InternalAuthController`** — `@RestController @RequestMapping("/internal/auth")`:
   ```java
   @PostMapping("/validate-token")
   public ResponseEntity<ValidateTokenResponse> validateToken(@RequestHeader("Authorization") String authHeader) {
       String token = authHeader.replaceFirst("^Bearer\\s+", "");
       try {
           JwtClaims claims = jwtTokenValidator.validate(token);
           return ResponseEntity.ok(new ValidateTokenResponse(
               claims.sub(), claims.email(), claims.role().name(), claims.merchantId()));
       } catch (JwtValidationException e) {
           // 401 Problem Details
           throw e; // GlobalExceptionHandler trata
       }
   }
   ```
3. **`ValidateTokenResponse` Record** — `(UUID userId, String email, String role, UUID merchantId)`.

### REFACTOR
- Para sub-50ms P99: medir; se necessário, cachear `PublicKey` parseado (já feito na task-11 GREEN).
- Logging: `logger.debug("Token validated: userId={}", claims.sub())` — nunca o token.
- Garantir comparação constant-time (`MessageDigest.isEqual`) — evita timing oracle no segredo.

## Acceptance Criteria
- [ ] `InternalSecretFilterTest` passa (5 testes), cobrindo CE-INTERNAL-001 e CE-INTERNAL-002
- [ ] `InternalAuthControllerIT` passa (5 testes)
- [ ] Filter retorna 403 com Problem Details (RFC 7807) quando secret ausente/inválido
- [ ] Filter NÃO afeta rotas que não começam com `/internal/`
- [ ] Comparação de secret usa `MessageDigest.isEqual` (constant-time)
- [ ] Endpoint retorna 200 com `userId`, `email`, `role`, `merchantId` em JWT válido
- [ ] Endpoint retorna 401 em JWT inválido/expirado
- [ ] Logs NUNCA incluem o JWT completo nem o INTERNAL_SECRET
