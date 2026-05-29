# Task 21: TwoFactorController — 5 endpoints 2FA

## Objective
Implementar `TwoFactorController` com endpoints `POST /api/v1/auth/2fa/setup`, `/confirm`, `/verify`, `/disable`, `/recovery`. Delega para `TwoFactorService` (task-16) e usa `GlobalExceptionHandler` (task-20) para erros.

## Context
**Quick Context:**
- `/setup`, `/confirm`, `/disable` requerem JWT (autenticado).
- `/verify` e `/recovery` são públicos no caminho — usam `twoFactorToken` temporário (gerado no login quando 2FA ativo, gravado em `2fa_login:{token}` TTL 5 min).
- Sucesso de `/verify` e `/recovery` retorna mesmo formato de `AuthResult.Success` (accessToken + cookie refreshToken).

Ler antes:
- `specs/user-service/spec.md` §6 (2FA endpoints completos)
- `tasks/dev2/task-16` (TwoFactorService API)
- `tasks/dev2/task-20` (padrão de Controller + RFC 7807)

## Target Files
**Create:**
- `services/user-service/src/main/java/com/acaboumony/user/controller/TwoFactorController.java`
- `services/user-service/src/test/java/com/acaboumony/user/controller/TwoFactorControllerTest.java` (`@WebMvcTest`)

## Dependencies
- Depends on: task-16, task-20
- Blocks: task-23, task-24

## TDD Mode

### RED
`TwoFactorControllerTest`:
- `deve_retornar_200_com_secret_qrCode_e_recoveryCodes_quando_POST_2fa_setup_autenticado()`.
- `deve_retornar_401_quando_POST_2fa_setup_sem_JWT()`.
- `deve_retornar_409_TWO_FACTOR_ALREADY_ENABLED_quando_setup_em_user_com_2FA_ativo()`.
- `deve_retornar_200_quando_POST_2fa_confirm_com_code_valido()`.
- `deve_retornar_401_INVALID_TOTP_CODE_quando_confirm_com_code_invalido()`.
- `deve_retornar_200_com_AuthResult_Success_quando_POST_2fa_verify_com_twoFactorToken_e_code_validos()` — assert cookie refreshToken setado.
- `deve_retornar_401_INVALID_TOTP_CODE_quando_verify_com_code_invalido()`.
- `deve_retornar_204_quando_POST_2fa_disable_com_senha_e_code_validos()`.
- `deve_retornar_401_quando_disable_com_senha_errada()`.
- `deve_retornar_200_com_AuthResult_Success_quando_POST_2fa_recovery_com_code_valido()`.
- `deve_retornar_401_RECOVERY_CODE_INVALID_quando_recovery_com_code_invalido()`.
- `deve_retornar_422_RECOVERY_CODE_EXHAUSTED_quando_todos_recovery_codes_usados()`.

Roda → falha.

### GREEN
```java
@RestController
@RequestMapping("/api/v1/auth/2fa")
@RequiredArgsConstructor
public class TwoFactorController {

    private final TwoFactorService twoFactorService;

    @PostMapping("/setup")
    public TwoFactorSetupResponse setup(@AuthenticationPrincipal JwtAuthenticationToken jwt) {
        return twoFactorService.setup(UUID.fromString(jwt.getName()));
    }

    @PostMapping("/confirm")
    public ResponseEntity<Void> confirm(@AuthenticationPrincipal JwtAuthenticationToken jwt,
                                         @Valid @RequestBody TwoFactorConfirmRequest req) {
        twoFactorService.confirm(UUID.fromString(jwt.getName()), req.code());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/verify")
    public ResponseEntity<?> verify(@Valid @RequestBody TwoFactorVerifyRequest req, HttpServletResponse res) {
        AuthResult result = twoFactorService.verifyTwoFactorToken(req.twoFactorToken(), req.code());
        // mesma lógica de AuthController.login para Success/Failure
    }

    @PostMapping("/disable")
    public ResponseEntity<Void> disable(@AuthenticationPrincipal JwtAuthenticationToken jwt,
                                         @Valid @RequestBody TwoFactorDisableRequest req) {
        twoFactorService.disable(UUID.fromString(jwt.getName()), req.password(), req.code());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/recovery")
    public ResponseEntity<?> recovery(@Valid @RequestBody RecoveryCodeRequest req, HttpServletResponse res) {
        AuthResult result = twoFactorService.useRecoveryCodeAndLogin(req.twoFactorToken(), req.code());
        // mesma lógica de retorno com cookie
    }
}
```

### REFACTOR
- Extrair `setRefreshCookie` para utility compartilhada com `AuthController` (criar `CookieHelper` no pacote `controller`).
- Garantir que `/verify` e `/recovery` NÃO requerem JWT no SecurityConfig (task-23 cuida disso).

## Acceptance Criteria
- [ ] `TwoFactorControllerTest` passa (12+ testes)
- [ ] `/setup`, `/confirm`, `/disable` requerem JWT
- [ ] `/verify`, `/recovery` são públicos (autenticação via `twoFactorToken`)
- [ ] `/verify` e `/recovery` setam cookie `refreshToken` em caso de sucesso
- [ ] Erros mapeados via `GlobalExceptionHandler` (RFC 7807)
- [ ] `setup` retorna `secret`, `qrCodeUrl`, `otpAuthUrl`, `recoveryCodes` (8 codes)
