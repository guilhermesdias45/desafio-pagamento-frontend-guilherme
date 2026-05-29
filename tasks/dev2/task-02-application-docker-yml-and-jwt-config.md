# Task 02: application-docker.yml + JwtConfig stub (env bindings)

## Objective
Atualizar `application-docker.yml` para refletir as env vars renomeadas em task-01 (`TOTP_AES_KEY` em vez de `AES_SECRET_KEY`, novas `INTERNAL_SECRET` e `TOTP_ISSUER`). Adicionar propriedades correspondentes em `application.yml` com defaults para dev local. Criar `JwtConfig.java` mínimo (apenas estrutura `@ConfigurationProperties` — não implementar parsing das chaves ainda; isso vai em task-11).

## Context
**Quick Context:**
- `application-docker.yml` linha 45-46 atualmente mapeia `aes.secret-key: ${AES_SECRET_KEY}` — precisa virar `totp.aes-key: ${TOTP_AES_KEY}`.
- `application.yml` precisa dos defaults locais para `internal.secret`, `totp.issuer`, `totp.aes-key` (rodar testes unitários sem precisar de Docker).
- Esta task é a fundação para `JwtTokenProvider` (task-11), `InternalSecretFilter` (task-19) e `TwoFactorService` (task-16) lerem suas configs via `@Value` ou `@ConfigurationProperties`.
- Já existe propriedade `jwt.access-token-expiration-seconds`, `jwt.refresh-token-expiration-seconds`, `jwt.two-factor-token-expiration-seconds` em `application.yml` — manter.
- **Funcional? Parcialmente** — não há lógica de runtime ainda (só binding). TDD: escrever 1 teste de Spring context startup que falha antes do binding existir.

Ler antes:
- `services/user-service/src/main/resources/application.yml`
- `services/user-service/src/main/resources/application-docker.yml`
- `specs/user-service/plan.md` §"Variáveis de Ambiente"

## Target Files
**Modify:**
- `services/user-service/src/main/resources/application.yml`
- `services/user-service/src/main/resources/application-docker.yml`

**Create:**
- `services/user-service/src/main/java/com/acaboumony/user/config/JwtProperties.java`
- `services/user-service/src/main/java/com/acaboumony/user/config/TotpProperties.java`
- `services/user-service/src/main/java/com/acaboumony/user/config/InternalSecretProperties.java`
- `services/user-service/src/main/java/com/acaboumony/user/config/JwtConfig.java` (apenas `@EnableConfigurationProperties` por enquanto)
- `services/user-service/src/test/java/com/acaboumony/user/config/ConfigurationPropertiesTest.java`

## Dependencies
- Depends on: task-01
- Blocks: task-11, task-16, task-19

## TDD Mode

### RED
Em `ConfigurationPropertiesTest.java` (`@SpringBootTest(classes = UserServiceApplication.class)` + `@TestPropertySource` com valores dummy), escrever:
- `deve_carregar_jwt_properties_com_chaves_e_expiracoes_quando_context_sobe()`
- `deve_carregar_totp_properties_com_aes_key_e_issuer_quando_context_sobe()`
- `deve_carregar_internal_secret_properties_quando_context_sobe()`

Cada teste injeta o respectivo `@ConfigurationProperties` bean e assert que os valores não são null e batem com `@TestPropertySource`. Roda — falha porque os beans não existem.

### GREEN
1. Criar `JwtProperties` como `record` com campos `privateKey` (String, base64 PEM), `publicKey` (String, base64 PEM), `accessTokenExpirationSeconds` (int), `refreshTokenExpirationSeconds` (int), `twoFactorTokenExpirationSeconds` (int). Anotar `@ConfigurationProperties(prefix = "jwt")`.
2. Criar `TotpProperties` como `record` com `aesKey` (String, hex 64 chars), `issuer` (String). Prefix `totp`.
3. Criar `InternalSecretProperties` como `record` com `secret` (String). Prefix `internal`.
4. `JwtConfig.java`: classe `@Configuration` com `@EnableConfigurationProperties({JwtProperties.class, TotpProperties.class, InternalSecretProperties.class})`.
5. Atualizar `application.yml` com defaults para dev local:
   ```yaml
   jwt:
     private-key: ${JWT_PRIVATE_KEY:dev-fixture-not-used-in-tests}
     public-key: ${JWT_PUBLIC_KEY:dev-fixture-not-used-in-tests}
     access-token-expiration-seconds: 900
     refresh-token-expiration-seconds: 604800
     two-factor-token-expiration-seconds: 300
   totp:
     issuer: ${TOTP_ISSUER:AcabouoMony}
     aes-key: ${TOTP_AES_KEY:00000000000000000000000000000000000000000000000000000000000000}
   internal:
     secret: ${INTERNAL_SECRET:dev-internal-secret}
   ```
6. Atualizar `application-docker.yml`: remover bloco `aes:`, deixar `jwt.*` como já está (já lê env vars). Adicionar:
   ```yaml
   totp:
     aes-key: ${TOTP_AES_KEY}
     issuer: ${TOTP_ISSUER:AcabouoMony}
   internal:
     secret: ${INTERNAL_SECRET}
   ```

### REFACTOR
- Garantir que os 3 records têm validação Bean Validation onde aplicável (`@NotBlank` em strings críticas) com `@Validated` no `JwtConfig`.
- Verificar que o `@TestPropertySource` no teste cobre todos os campos obrigatórios.

## Acceptance Criteria
- [ ] `ConfigurationPropertiesTest` passa (3 testes verdes)
- [ ] `./mvnw test -Dtest=ConfigurationPropertiesTest` retorna build success
- [ ] `application.yml` contém defaults para todas as novas propriedades — `./mvnw spring-boot:run` (sem Docker) não falha por env var ausente
- [ ] `application-docker.yml` não contém mais `aes.secret-key`; contém `totp.aes-key`, `totp.issuer`, `internal.secret`
- [ ] `JwtProperties`, `TotpProperties`, `InternalSecretProperties` são `record` Java 21 com `@ConfigurationProperties`
