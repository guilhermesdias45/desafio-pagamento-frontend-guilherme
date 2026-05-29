# Task 24: Integration tests E2E — fluxos completos via HTTP

## Objective
Cobrir cenários end-to-end com Testcontainers (PostgreSQL + Redis + Kafka via `BaseIntegrationTest`): registro → confirmação → login → refresh (com rotação) → logout; setup 2FA completo; uso de recovery code; validação via `/internal/auth/validate-token`. Verifica que todos os componentes funcionam juntos com Spring Security + Flyway + Kafka real.

## Context
**Quick Context:**
- Última task da pipeline. Garante que cobertura JaCoCo ≥ 90% global.
- Usa `MockMvc` ou `TestRestTemplate` ou RestAssured para fazer chamadas HTTP reais ao Spring context. Preferência: `MockMvc` (mais rápido, integrado com Spring Security Test).
- Consumir Kafka events em testes: usar `KafkaTestConsumer` helper que subscreve no tópico `user-events` antes do teste.

Ler antes:
- `tasks/dev2/updated-prd.md` §5 (matriz CE → task)
- `tasks/dev2/task-10` (BaseIntegrationTest)
- Todas as tasks anteriores (entender APIs)

## Target Files
**Create:**
- `services/user-service/src/test/java/com/acaboumony/user/e2e/RegistrationLoginFlowIT.java`
- `services/user-service/src/test/java/com/acaboumony/user/e2e/RefreshRotationFlowIT.java`
- `services/user-service/src/test/java/com/acaboumony/user/e2e/TwoFactorFullFlowIT.java`
- `services/user-service/src/test/java/com/acaboumony/user/e2e/InternalValidateTokenFlowIT.java`
- `services/user-service/src/test/java/com/acaboumony/user/support/KafkaTestConsumer.java` (helper)

## Dependencies
- Depends on: task-10, task-20, task-21, task-22, task-23 (toda a stack pronta)
- Blocks: nada (última task)

## TDD Mode

### RED
**`RegistrationLoginFlowIT extends BaseIntegrationTest` (com `@AutoConfigureMockMvc`)**:
- `deve_completar_fluxo_register_CUSTOMER_confirm_email_login_e_obter_cookie_refresh()`:
  1. POST /auth/register payload CUSTOMER → 201
  2. Verifica evento `user.registered` no Kafka (via KafkaTestConsumer)
  3. Lê token `email_confirm:*` do Redis
  4. POST /auth/confirm-email com token → 200
  5. POST /auth/login → 200 com accessToken no body + Set-Cookie refreshToken HttpOnly
  6. Verifica evento `user.login.success` no Kafka
- `deve_completar_fluxo_register_MERCHANT_OWNER_atomico_user_e_merchant()`:
  1. POST /auth/register payload MERCHANT_OWNER → 201 com merchantId não-null
  2. Verifica via SQL: User.merchant_id == Merchant.id, Merchant.owner_id == User.id
  3. Evento `user.registered` com merchantId no payload
- `deve_rejeitar_login_com_status_PENDING_quando_email_nao_confirmado()` (CE-LOGIN-003).
- `deve_bloquear_conta_apos_5_tentativas_e_publicar_user_login_blocked()` (CE-LOGIN-001).

**`RefreshRotationFlowIT extends BaseIntegrationTest`**:
- `deve_rotacionar_refresh_token_e_invalidar_o_antigo()`:
  1. Login → cookie refreshToken=A
  2. POST /auth/refresh com cookie=A → 200, novo cookie refreshToken=B, A deletado do Redis
  3. POST /auth/refresh com cookie=A novamente → 401 (CE-REFRESH-001)
  4. POST /auth/refresh com cookie=B → 200 ok

**`TwoFactorFullFlowIT extends BaseIntegrationTest`**:
- `deve_completar_fluxo_setup_confirm_login_com_2FA()`:
  1. Login → cookie refresh
  2. POST /2fa/setup → secret + 8 recovery codes
  3. Gera código TOTP com a lib (mesma usada no service)
  4. POST /2fa/confirm com code → 200, evento `user.2fa.enabled`
  5. Logout
  6. POST /login sem totpCode → 200 com `requiresTwoFactor: true` + twoFactorToken
  7. POST /2fa/verify com twoFactorToken + code → 200 com accessToken e cookie refresh
- `deve_usar_recovery_code_e_marcar_used()`:
  1. Setup 2FA + confirm
  2. POST /2fa/recovery com twoFactorToken + recoveryCode[0] → 200
  3. POST /2fa/recovery com mesmo recoveryCode[0] → 401 RECOVERY_CODE_INVALID
  4. Usar 7 recovery codes restantes
  5. 8º falha com RECOVERY_CODE_EXHAUSTED (CE-2FA-001) — na verdade depois de usar todos os 8, falha com EXHAUSTED no próximo
- `deve_desativar_2FA_com_senha_e_code_validos()`.

**`InternalValidateTokenFlowIT extends BaseIntegrationTest`**:
- `deve_retornar_userId_email_role_merchantId_quando_POST_internal_validate_token_com_secret_e_JWT_validos()`.
- `deve_retornar_403_quando_sem_X_Internal_Secret()` (CE-INTERNAL-001).
- `deve_retornar_403_quando_X_Internal_Secret_invalido()` (CE-INTERNAL-002).
- `deve_retornar_401_quando_JWT_expirado()`.

Roda → falha (algum componente da stack não está integrado).

### GREEN
1. **`KafkaTestConsumer`** — helper que subscreve em `user-events`, retorna `List<ConsumerRecord<String, String>>` após X ms. Use `EmbeddedKafkaBroker` ou consumer direto contra container.
2. **Cada IT** — testes que orquestram HTTP calls + asserts. Helpers para gerar TOTP code dado um secret base32.
3. **Fixar problemas** descobertos: integrações que não funcionam (ex: cookie não persiste entre requests do MockMvc — usar `MockMvc.perform().andReturn().getResponse().getCookies()` e re-enviar).

### REFACTOR
- Extrair flow steps (`registerCustomer`, `confirmEmail`, `login`) para `AuthTestSteps` helper compartilhado.
- Garantir tear-down: limpar Redis e Kafka entre testes (ou usar `@DirtiesContext` se necessário — caro).

## Acceptance Criteria
- [ ] `RegistrationLoginFlowIT` passa (4+ testes E2E)
- [ ] `RefreshRotationFlowIT` passa (1 fluxo cobrindo CE-REFRESH-001)
- [ ] `TwoFactorFullFlowIT` passa (3+ fluxos cobrindo CE-2FA-001)
- [ ] `InternalValidateTokenFlowIT` passa (4 testes cobrindo CE-INTERNAL-001/002)
- [ ] Cobertura JaCoCo global ≥ 90% (verificar com `./mvnw verify` — `jacoco-maven-plugin:check` passa)
- [ ] Todos os CEs da spec.md têm pelo menos 1 teste verde apontando para eles (mapping em `updated-prd.md` §5 verificado)
- [ ] Zero dados sensíveis em logs (verificar com grep em `target/surefire-reports/`)
- [ ] Eventos Kafka publicados corretamente em `user-events` (key=userId, eventType discriminator)
