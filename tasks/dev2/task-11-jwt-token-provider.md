# Task 11: JwtTokenProvider + JwtTokenValidator (RS256)

## Objective
Implementar geração e validação de JWT access token RS256 usando JJWT 0.12.6. Claims: `sub` (userId), `email`, `role`, `merchantId` (nullable), `iat`, `exp` (15 min). Chave RSA lida de `JwtProperties` (PEM base64).

## Context
**Quick Context:**
- JJWT 0.12.6 mudou a API — usar `Jwts.builder()...signWith(privateKey, Jwts.SIG.RS256)` e `Jwts.parser().verifyWith(publicKey).build().parseSignedClaims(token)`.
- `JwtProperties.privateKey()` e `.publicKey()` retornam strings base64 do PEM (com `\n` literal); precisam ser decodificados via `KeyFactory.getInstance("RSA")` + `PKCS8EncodedKeySpec` (private) / `X509EncodedKeySpec` (public). Em `BaseIntegrationTest` os defaults são dummy — testes desta task usam `@TestPropertySource` com chaves RSA reais geradas inline ou em `src/test/resources/test-keys/`.
- `JwtTokenValidator.validate(String token)` retorna `JwtClaims` (record com sub, email, role, merchantId) ou lança `JwtValidationException` (token expirado, assinatura inválida, claims faltantes).
- **Crítico para api-gateway** — usado por `InternalAuthController` em task-19.

Ler antes:
- `specs/user-service/spec.md` §4.5 (JWT claims)
- `specs/user-service/plan.md` §"JWT" (linhas 203-221)
- `services/user-service/pom.xml` (versões JJWT)

## Target Files
**Create:**
- `services/user-service/src/main/java/com/acaboumony/user/security/JwtTokenProvider.java`
- `services/user-service/src/main/java/com/acaboumony/user/security/JwtTokenValidator.java`
- `services/user-service/src/main/java/com/acaboumony/user/security/JwtClaims.java` (Record imutável)
- `services/user-service/src/main/java/com/acaboumony/user/security/JwtValidationException.java`
- `services/user-service/src/main/java/com/acaboumony/user/security/RsaKeyLoader.java` (utility para decodificar PEM base64)
- `services/user-service/src/test/java/com/acaboumony/user/security/JwtTokenProviderTest.java`
- `services/user-service/src/test/java/com/acaboumony/user/security/JwtTokenValidatorTest.java`
- `services/user-service/src/test/resources/test-keys/test-private-key.pem` (RSA 2048 — gerada uma vez para testes)
- `services/user-service/src/test/resources/test-keys/test-public-key.pem`

## Dependencies
- Depends on: task-02 (JwtProperties), task-07 (UserRole)
- Blocks: task-15, task-17, task-19, task-23

## TDD Mode

### RED
`JwtTokenProviderTest` (unitário, sem Spring — instanciar `JwtTokenProvider` manualmente com chaves dummy):
- `deve_gerar_token_rs256_com_claims_corretos_quando_generateAccessToken_chamado()` — payload contém `sub`, `email`, `role`, `merchantId`, `iat`, `exp`.
- `deve_setar_exp_em_15_minutos_a_partir_de_iat_quando_generateAccessToken()` — `exp - iat == 900`.
- `deve_incluir_merchantId_quando_role_e_MERCHANT_OWNER()`.
- `deve_setar_merchantId_null_quando_role_e_CUSTOMER()` — não confundir com "claim ausente"; valor pode ser `null` no JSON.

`JwtTokenValidatorTest`:
- `deve_validar_token_e_retornar_claims_quando_token_assinado_corretamente()`.
- `deve_lancar_JwtValidationException_quando_assinatura_invalida()` (token assinado com outra chave).
- `deve_lancar_JwtValidationException_quando_token_expirado()` (gerar com `exp` no passado).
- `deve_lancar_JwtValidationException_quando_token_malformado()` (string `"not.a.jwt"`).
- `deve_lancar_JwtValidationException_quando_claims_obrigatorios_ausentes()` (token assinado mas sem `email` ou `role`).

Roda → falha (classes não existem).

### GREEN
1. **`RsaKeyLoader`** — métodos estáticos `loadPrivateKey(String base64Pem)` e `loadPublicKey(String base64Pem)`. Strip headers `-----BEGIN ... KEY-----`, decode base64, `KeyFactory.getInstance("RSA")` + spec.
2. **`JwtClaims` record** — `(UUID sub, String email, UserRole role, UUID merchantId, Instant issuedAt, Instant expiresAt)`. `merchantId` pode ser `null`.
3. **`JwtTokenProvider`** — `@Component`, injeta `JwtProperties`. Método `generateAccessToken(UUID userId, String email, UserRole role, UUID merchantId): String`. Usa `Jwts.builder().subject(userId.toString()).claim("email", email).claim("role", role.name()).claim("merchantId", merchantId != null ? merchantId.toString() : null).issuedAt(Date.from(now)).expiration(Date.from(now.plusSeconds(900))).signWith(privateKey, Jwts.SIG.RS256).compact()`.
4. **`JwtTokenValidator`** — `@Component`, injeta `JwtProperties`. Método `validate(String token): JwtClaims`. Usa `Jwts.parser().verifyWith(publicKey).build().parseSignedClaims(token)`. Captura `ExpiredJwtException`, `SignatureException`, `MalformedJwtException` → rethrow como `JwtValidationException` (com `errorCode` específico: `REFRESH_TOKEN_EXPIRED`, `INVALID_SIGNATURE`, `MALFORMED_TOKEN`).
5. **`JwtValidationException`** — `extends RuntimeException`, campo `errorCode String`.
6. **Test fixtures** — gerar par RSA 2048 em `test-keys/` (uma vez, via `openssl` ou script gerador). Os testes carregam essas chaves via `Files.readString`.

### REFACTOR
- Cachear `PrivateKey` e `PublicKey` em campos do bean (parsing PEM é caro; fazer no `@PostConstruct`).
- `JwtClaims.fromMap(Map<String, Object>)` factory method para reduzir parsing manual no validator.
- Logging: usar `logger.debug` para sucesso, `logger.warn` para falha de validação (sem logar o token).

## Acceptance Criteria
- [ ] `JwtTokenProviderTest` passa (4+ testes)
- [ ] `JwtTokenValidatorTest` passa (5+ testes)
- [ ] Token gerado por `JwtTokenProvider` é validado com sucesso por `JwtTokenValidator` (round-trip)
- [ ] Token expirado é rejeitado com `errorCode = "REFRESH_TOKEN_EXPIRED"`
- [ ] Token com assinatura inválida é rejeitado com `errorCode = "INVALID_SIGNATURE"`
- [ ] Claims `sub`, `email`, `role`, `merchantId`, `iat`, `exp` presentes no payload
- [ ] Logs nunca incluem o token completo (verificar com `grep` no output do teste)
- [ ] Cobertura JaCoCo do pacote `security` (apenas estas classes) ≥ 95%
