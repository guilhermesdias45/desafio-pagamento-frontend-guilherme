# Task 16: TwoFactorService — setup/confirm/verify/disable/recovery + AES-256-GCM + BCrypt recovery codes

## Objective
Implementar todo o ciclo de vida do 2FA TOTP: setup (gera secret + QR code + 8 recovery codes), confirm (ativa após primeiro código válido), verify (durante login), disable (com senha + código), recovery (usa recovery code para login de emergência). Secret TOTP armazenado **AES-256-GCM**; recovery codes hasheados com BCrypt.

## Context
**Quick Context:**
- Lib: `dev.samstevens.totp:1.7.1` (já no `pom.xml`). API: `DefaultSecretGenerator`, `DefaultCodeVerifier`, `QrGenerator`.
- TOTP: HMAC-SHA1, period 30s, 6 dígitos, tolerância ±1 janela. Lib trata isso default.
- **AES-256-GCM**: usar `Cipher.getInstance("AES/GCM/NoPadding")` com `GCMParameterSpec(128, iv)`. IV gerado aleatoriamente por encryption (12 bytes), prefixo do ciphertext.
- 8 recovery codes únicos (formato: 4 grupos de 4 chars alfanuméricos `XXXX-XXXX-XXXX-XXXX`). Hash BCrypt rounds=12. Cada uso marca `used=true, used_at=NOW()`.
- `RECOVERY_CODE_EXHAUSTED` quando todos os 8 estão usados (CE-2FA-001).
- Evento Kafka `user.2fa.enabled` ao ativar.
- Setup temporário (antes do confirm): secret guardado em Redis `2fa_setup:{userId}` TTL 10 min (não persiste no DB até confirmar).

Ler antes:
- `specs/user-service/spec.md` §6 (2FA) inteiro
- `specs/user-service/plan.md` linhas 19-23 (decisões 2FA)
- `tasks/dev2/task-08` (User entity, RecoveryCode entity, RecoveryCodeRepository)
- `tasks/dev2/task-12` (UserEventProducer)
- `tasks/dev2/updated-prd.md` §5 (CE-2FA-001)

## Target Files
**Create:**
- `services/user-service/src/main/java/com/acaboumony/user/service/TwoFactorService.java`
- `services/user-service/src/main/java/com/acaboumony/user/service/AesGcmCryptoService.java` (encrypt/decrypt secret TOTP)
- `services/user-service/src/main/java/com/acaboumony/user/dto/request/TwoFactorConfirmRequest.java`
- `services/user-service/src/main/java/com/acaboumony/user/dto/request/TwoFactorVerifyRequest.java`
- `services/user-service/src/main/java/com/acaboumony/user/dto/request/TwoFactorDisableRequest.java`
- `services/user-service/src/main/java/com/acaboumony/user/dto/request/RecoveryCodeRequest.java`
- `services/user-service/src/main/java/com/acaboumony/user/dto/response/TwoFactorSetupResponse.java`
- `services/user-service/src/main/java/com/acaboumony/user/exception/InvalidTotpCodeException.java`
- `services/user-service/src/main/java/com/acaboumony/user/exception/TwoFactorAlreadyEnabledException.java`
- `services/user-service/src/main/java/com/acaboumony/user/exception/TwoFactorNotEnabledException.java`
- `services/user-service/src/main/java/com/acaboumony/user/exception/RecoveryCodeInvalidException.java`
- `services/user-service/src/main/java/com/acaboumony/user/exception/RecoveryCodeExhaustedException.java`
- `services/user-service/src/test/java/com/acaboumony/user/service/AesGcmCryptoServiceTest.java`
- `services/user-service/src/test/java/com/acaboumony/user/service/TwoFactorServiceIT.java`

## Dependencies
- Depends on: task-02 (TotpProperties), task-08, task-12, task-15 (UserAuditLogger)
- Blocks: task-21

## TDD Mode

### RED
**`AesGcmCryptoServiceTest`** (unitário):
- `deve_encriptar_e_decriptar_recuperando_plaintext_original()`.
- `deve_gerar_ciphertexts_diferentes_para_mesmo_plaintext_quando_encrypt_chamado_2x()` (IV aleatório).
- `deve_lancar_excecao_quando_decrypt_em_ciphertext_modificado()` (GCM detecta tampering).
- `deve_lancar_excecao_quando_chave_AES_invalida()` (não 32 bytes).

**`TwoFactorServiceIT extends BaseIntegrationTest`**:
- `deve_retornar_secret_e_qrCode_e_8_recovery_codes_quando_setup()`.
- `deve_armazenar_secret_em_redis_e_NAO_em_db_quando_setup()` (Redis key `2fa_setup:{userId}` TTL 10 min).
- `deve_rejeitar_setup_quando_user_ja_tem_2FA_ativo()` (**TWO_FACTOR_ALREADY_ENABLED**).
- `deve_ativar_2FA_e_persistir_secret_encriptado_quando_confirm_com_code_valido()` — `User.totpEnabled == true`, `User.totpSecretEncrypted != null`, evento `user.2fa.enabled` publicado, 8 recovery codes salvos em DB com BCrypt hash.
- `deve_rejeitar_confirm_quando_code_invalido()` (**INVALID_TOTP_CODE**).
- `deve_rejeitar_confirm_quando_setup_expirou_no_redis()`.
- `deve_validar_code_no_verify_durante_login()` (test helper para task-15).
- `deve_rejeitar_disable_quando_senha_errada()`.
- `deve_rejeitar_disable_quando_code_errado()`.
- `deve_desativar_2FA_e_deletar_recovery_codes_quando_disable_com_senha_e_code_validos()`.
- `deve_aceitar_recovery_code_uma_vez_e_marcar_used()`.
- `deve_rejeitar_recovery_code_quando_ja_usado()`.
- `deve_rejeitar_recovery_code_quando_hash_nao_bate()` (**RECOVERY_CODE_INVALID**).
- `deve_lancar_RECOVERY_CODE_EXHAUSTED_quando_todos_8_codes_usados()` (**CE-2FA-001**).
- `deve_tolerar_janela_+-1_de_30s_no_verify_TOTP()` (lib trata; smoke test).

Roda → falha.

### GREEN
1. **`AesGcmCryptoService`** — `@Service`, injeta `TotpProperties` (lê `aesKey` hex 64 chars = 32 bytes).
   ```java
   public String encrypt(String plaintext) {
       byte[] iv = new byte[12]; new SecureRandom().nextBytes(iv);
       Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
       cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(128, iv));
       byte[] ct = cipher.doFinal(plaintext.getBytes(UTF_8));
       byte[] out = ByteBuffer.allocate(iv.length + ct.length).put(iv).put(ct).array();
       return Base64.getEncoder().encodeToString(out);
   }
   public String decrypt(String base64) {
       byte[] in = Base64.getDecoder().decode(base64);
       byte[] iv = Arrays.copyOfRange(in, 0, 12);
       byte[] ct = Arrays.copyOfRange(in, 12, in.length);
       Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
       cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(128, iv));
       return new String(cipher.doFinal(ct), UTF_8);
   }
   ```
2. **`TwoFactorService`** — `@Service` com:
   - `setup(UUID userId): TwoFactorSetupResponse` — gera secret (`DefaultSecretGenerator`), `otpauth://` URI com issuer = `TotpProperties.issuer()`, QR code via `QrGenerator` (PNG → base64), 8 recovery codes plaintext. Armazena secret em Redis `2fa_setup:{userId}` TTL 10 min. **Não persiste em DB ainda.** Retorna response com secret, qrCodeUrl, otpAuthUrl, recoveryCodes (plaintext).
   - `confirm(UUID userId, String code)` — busca secret em Redis; valida code via `DefaultCodeVerifier`; se OK: encripta secret com AES-256-GCM, salva em `User.totpSecretEncrypted`, `User.totpEnabled = true`; hash de cada recovery code com BCrypt e persiste em `recovery_codes`; deleta Redis key; publica `user.2fa.enabled`; audit log.
   - `verifyTotp(User user, String code): boolean` — usado por AuthService.authenticate (task-15). Decripta secret, chama `DefaultCodeVerifier`.
   - `verifyTwoFactorToken(String twoFactorToken, String code): AuthResult` — usado pelo controller no fluxo de 2FA gate. Busca userId em `2fa_login:{token}`, valida code, gera JWT + refresh, deleta key.
   - `disable(UUID userId, String password, String code)` — verifica senha (BCrypt matches), verifica code TOTP; se OK: `totpEnabled = false`, `totpSecretEncrypted = null`, deleta todos recovery_codes do user.
   - `useRecoveryCode(UUID userId, String code): boolean` — busca `RecoveryCode` não-usados do user; BCrypt.matches para cada um até achar; marca `used = true, used_at = NOW()`. Se nenhum bate → `RECOVERY_CODE_INVALID`. Se zero recovery codes não-usados antes da chamada → `RECOVERY_CODE_EXHAUSTED`.
3. **DTOs Records** conforme spec.md §6.
4. **`TwoFactorSetupResponse`** Record: `(String secret, String qrCodeUrl, String otpAuthUrl, List<String> recoveryCodes)`.
5. **Exceptions** com `errorCode` para o `GlobalExceptionHandler` (task-20).

### REFACTOR
- Cachear instância de `DefaultCodeVerifier` e `DefaultSecretGenerator` (thread-safe? confirmar; senão criar por chamada).
- Logging: `logger.info("2FA enabled: userId={}", userId)` — NUNCA logar secret, code, recovery code.
- Considerar `RecoveryCodeGenerator` como classe separada para testabilidade — opcional.

## Acceptance Criteria
- [ ] `AesGcmCryptoServiceTest` passa (4 testes)
- [ ] `TwoFactorServiceIT` passa (15+ testes), incluindo CE-2FA-001
- [ ] Secret TOTP persistido **AES-256-GCM** (não plaintext, não AES-CBC)
- [ ] Mesmo secret encriptado 2x produz ciphertexts diferentes (IV aleatório)
- [ ] Recovery codes persistidos como BCrypt hash, plaintext exibido apenas no setup
- [ ] Cada recovery code usável apenas 1x (marca `used`)
- [ ] `RECOVERY_CODE_EXHAUSTED` quando todos 8 já usados
- [ ] Evento `user.2fa.enabled` publicado no Kafka
- [ ] `TWO_FACTOR_ALREADY_ENABLED` ao tentar setup com 2FA ativo
- [ ] Logs NUNCA contêm secret, code, recovery code, chave AES
- [ ] Cobertura JaCoCo do pacote ≥ 90%
