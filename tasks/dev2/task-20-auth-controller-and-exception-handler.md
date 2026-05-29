# Task 20: AuthController + GlobalExceptionHandler (RFC 7807)

## Objective
Implementar `AuthController` com endpoints `POST /register`, `POST /confirm-email`, `POST /login`, `POST /refresh`, `POST /logout`, `POST /resend-confirmation` (stub 501). Implementar `GlobalExceptionHandler` (`@RestControllerAdvice`) que mapeia todas as exceptions de domínio para responses RFC 7807 Problem Details.

## Context
**Quick Context:**
- Controllers só orquestram — sem lógica de negócio.
- **httpOnly cookie**: `refreshToken` SEMPRE em cookie `Set-Cookie: refreshToken=<uuid>; HttpOnly; Secure; SameSite=Strict; Path=/api/v1/auth; Max-Age=604800`. NUNCA no body da resposta.
- **RFC 7807** Problem Details: response body `{ type, title, status, detail, instance, errorCode, retryable, [unlockAt] }`. Content-Type `application/problem+json`.
- POST `/auth/confirm-email` (corpo `{ "token": "..." }`): busca `email_confirm:{token}` no Redis, lê userId, marca `user.status = ACTIVE`, deleta a key. Ver Open Question em `updated-prd.md` §11.
- `POST /resend-confirmation` retorna 501 com `errorCode = "NOT_IMPLEMENTED_SPRINT_2"`.

Ler antes:
- `specs/user-service/spec.md` §2 (lista de endpoints), §3-§5
- `tasks/dev2/task-14`, `task-15`, `task-17`, `task-18` (services consumidos)
- `tasks/dev2/updated-prd.md` §7 e §11 (Open Questions sobre confirm-email)

## Target Files
**Create:**
- `services/user-service/src/main/java/com/acaboumony/user/controller/AuthController.java`
- `services/user-service/src/main/java/com/acaboumony/user/controller/GlobalExceptionHandler.java`
- `services/user-service/src/main/java/com/acaboumony/user/dto/request/ConfirmEmailRequest.java`
- `services/user-service/src/main/java/com/acaboumony/user/exception/UserServiceException.java` (base abstract)
- `services/user-service/src/test/java/com/acaboumony/user/controller/AuthControllerTest.java` (`@WebMvcTest`)
- `services/user-service/src/test/java/com/acaboumony/user/controller/AuthControllerIT.java` (full stack)

## Dependencies
- Depends on: task-14, task-15, task-17, task-18
- Blocks: task-23, task-24

## TDD Mode

### RED
**`AuthControllerTest`** (`@WebMvcTest(AuthController.class)` + Mockito para AuthService):

- `deve_retornar_201_com_RegisterResponse_quando_POST_register_payload_valido()`.
- `deve_retornar_400_problem_details_quando_POST_register_payload_invalido_email_blank()` — RFC 7807 com `errorCode = "VALIDATION_FAILED"` e lista de field errors.
- `deve_retornar_409_EMAIL_ALREADY_EXISTS_quando_AuthService_lanca_EmailAlreadyExistsException()`.
- `deve_retornar_400_MISSING_MERCHANT_DATA_quando_MERCHANT_OWNER_sem_cnpj()`.
- `deve_retornar_400_INVALID_ROLE_quando_role_STAFF()`.
- `deve_retornar_200_e_setar_httpOnly_cookie_refreshToken_quando_POST_login_sucesso()` — assert `Set-Cookie` header contém `HttpOnly` e `Secure`.
- `deve_NAO_incluir_refreshToken_no_body_da_resposta_login()` — assert JSON do body não tem campo `refreshToken`.
- `deve_retornar_200_com_RequiresTwoFactor_quando_login_com_2FA_ativo_sem_code()`.
- `deve_retornar_401_INVALID_CREDENTIALS_quando_AuthService_retorna_Failure()`.
- `deve_retornar_423_ACCOUNT_LOCKED_com_unlockAt_no_problem_details()`.
- `deve_retornar_403_ACCOUNT_NOT_CONFIRMED_quando_status_PENDING()`.
- `deve_retornar_200_e_novo_cookie_quando_POST_refresh_com_token_valido()`.
- `deve_retornar_401_quando_POST_refresh_sem_cookie()`.
- `deve_retornar_204_quando_POST_logout()`.
- `deve_retornar_501_NOT_IMPLEMENTED_SPRINT_2_quando_POST_resend_confirmation()`.

**`AuthControllerIT extends BaseIntegrationTest`** (full stack, sem mocks):
- Smoke: `deve_completar_fluxo_register_confirm_login_refresh_logout_via_http()`.

Roda → falha.

### GREEN
1. **`AuthController`** — `@RestController @RequestMapping("/api/v1/auth")`:
   ```java
   @PostMapping("/register")
   public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest req) {
       return ResponseEntity.status(201).body(authService.register(req));
   }

   @PostMapping("/login")
   public ResponseEntity<?> login(@Valid @RequestBody LoginRequest req, HttpServletResponse res) {
       AuthResult result = authService.authenticate(req);
       return switch (result) {
           case AuthResult.Success s -> {
               setRefreshCookie(res, s.refreshToken());
               // body sem refreshToken
               yield ResponseEntity.ok(Map.of(
                   "accessToken", s.accessToken(),
                   "tokenType", s.tokenType(),
                   "expiresIn", s.expiresIn(),
                   "requiresTwoFactor", false));
           }
           case AuthResult.RequiresTwoFactor r -> ResponseEntity.ok(r);
           case AuthResult.Failure f -> mapFailure(f);
       };
   }

   @PostMapping("/refresh")
   public ResponseEntity<?> refresh(@CookieValue(name = "refreshToken", required = false) String token,
                                    HttpServletResponse res) {
       if (token == null) throw new RefreshTokenInvalidException();
       RefreshResponse r = authService.refresh(token);
       setRefreshCookie(res, r.refreshToken());
       return ResponseEntity.ok(Map.of(
           "accessToken", r.accessToken(),
           "tokenType", r.tokenType(),
           "expiresIn", r.expiresIn()));
   }

   @PostMapping("/logout")
   public ResponseEntity<Void> logout(@AuthenticationPrincipal JwtAuthenticationToken jwt,
                                       @CookieValue("refreshToken") String token) {
       authService.logout(UUID.fromString(jwt.getName()), token);
       return ResponseEntity.noContent().build();
   }

   @PostMapping("/confirm-email")
   public ResponseEntity<Void> confirmEmail(@Valid @RequestBody ConfirmEmailRequest req) {
       authService.confirmEmail(req.token());
       return ResponseEntity.ok().build();
   }

   @PostMapping("/resend-confirmation")
   public ResponseEntity<ProblemDetail> resendConfirmation() {
       ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_IMPLEMENTED, "Sprint 2 feature");
       pd.setProperty("errorCode", "NOT_IMPLEMENTED_SPRINT_2");
       return ResponseEntity.status(501).body(pd);
   }
   ```
2. **`AuthService.confirmEmail(String token)`** — nova método: busca `email_confirm:{token}` no Redis, lê userId; busca User; seta `status = ACTIVE`; salva; deleta Redis key. Lança `EmailConfirmTokenInvalidException` se token ausente/expirado.
3. **`GlobalExceptionHandler`** — `@RestControllerAdvice`:
   ```java
   @ExceptionHandler(UserServiceException.class)
   public ResponseEntity<ProblemDetail> handle(UserServiceException ex) {
       HttpStatus status = mapStatus(ex.getErrorCode());
       ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, ex.getMessage());
       pd.setProperty("errorCode", ex.getErrorCode());
       pd.setProperty("retryable", ex.isRetryable());
       if (ex instanceof AccountLockedException locked) pd.setProperty("unlockAt", locked.getUnlockAt());
       return ResponseEntity.status(status).body(pd);
   }

   @ExceptionHandler(MethodArgumentNotValidException.class)
   public ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException ex) {
       // RFC 7807 com field errors
   }
   ```
   Mapeamentos:
   - EMAIL_ALREADY_EXISTS → 409
   - WEAK_PASSWORD, INVALID_EMAIL_FORMAT, INVALID_ROLE, INVALID_CNPJ, MISSING_MERCHANT_DATA → 400
   - CNPJ_ALREADY_REGISTERED → 409
   - INVALID_CREDENTIALS, INVALID_TOTP_CODE, REFRESH_TOKEN_INVALID, REFRESH_TOKEN_EXPIRED, RECOVERY_CODE_INVALID → 401
   - ACCOUNT_LOCKED → 423
   - ACCOUNT_NOT_CONFIRMED, ACCOUNT_DISABLED → 403
   - TWO_FACTOR_ALREADY_ENABLED → 409
   - TWO_FACTOR_NOT_ENABLED, RECOVERY_CODE_EXHAUSTED → 422
   - TOO_MANY_REQUESTS → 429
4. **Helper `setRefreshCookie(res, token)`**:
   ```java
   ResponseCookie cookie = ResponseCookie.from("refreshToken", token)
       .httpOnly(true).secure(true).sameSite("Strict").path("/api/v1/auth").maxAge(604800).build();
   res.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
   ```

### REFACTOR
- Extrair lógica de `setRefreshCookie` e `mapFailure` para `AuthControllerHelper` se ficar grande.
- Garantir Content-Type `application/problem+json` em todos os erros (Spring 6 faz isso automaticamente para `ProblemDetail` se configurado).

## Acceptance Criteria
- [ ] `AuthControllerTest` passa (15+ testes)
- [ ] `AuthControllerIT` passa (smoke test do fluxo completo via MockMvc/RestAssured)
- [ ] `refreshToken` em **httpOnly cookie** com `Secure`, `SameSite=Strict`, `Path=/api/v1/auth`, `Max-Age=604800`
- [ ] `refreshToken` NUNCA no body da resposta REST
- [ ] Erros em RFC 7807 (`application/problem+json`) com `errorCode`, `retryable`, status correto
- [ ] `ACCOUNT_LOCKED` inclui `unlockAt` no problem details
- [ ] `POST /resend-confirmation` retorna 501 `NOT_IMPLEMENTED_SPRINT_2`
- [ ] `POST /confirm-email` consome token do Redis e marca user como ACTIVE
- [ ] Cobertura JaCoCo do pacote `controller` ≥ 90% (após task-21 e task-22 também)
