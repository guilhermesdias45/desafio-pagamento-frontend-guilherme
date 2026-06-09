# Especificação: Autenticar Usuário (Login)

**ID:** SPEC-AUTH-002  
**Serviço:** user-service  
**Status:** Aprovado  
**Revisores:** [x] PM [x] Arquiteto [x] QA [x] Security

---

## 1. Assinatura

```java
POST /api/v1/auth/login
Content-Type: application/json
Body: LoginRequest

// Service
public AuthResult login(LoginRequest request)
```

---

## 2. Tipos de Dados

### Input — LoginRequest

```java
public record LoginRequest(
    @NotBlank @Email String email,
    @NotBlank String password
) {}
```

### Output — AuthResult (sealed interface)

```java
public sealed interface AuthResult
    permits AuthResult.Success, AuthResult.RequiresTwoFactor, AuthResult.Failure {

    record Success(
        String accessToken,    // JWT RS256, expira em 900s
        String tokenType,      // "Bearer"
        int expiresIn,         // 900
        boolean requiresTwoFactor  // false
    ) implements AuthResult {}

    record RequiresTwoFactor(
        boolean requiresTwoFactor,  // true
        String twoFactorToken       // token temporário 5 min para completar 2FA
    ) implements AuthResult {}

    record Failure(
        String errorCode,
        String message,
        boolean retryable
    ) implements AuthResult {}
}
```

> `refreshToken` (UUID opaque) retornado **apenas** em cookie `httpOnly; Secure; SameSite=Strict` — nunca no body.

---

## 3. Pré-condições

- `email` com formato válido
- `password` com pelo menos 8 chars
- Conta não está bloqueada (`account_locked:{email}` não existe no Redis)
- Taxa de tentativas não excedida (< 5 falhas na janela de 30 min)

---

## 4. Pós-condições (Sucesso — sem 2FA)

- `accessToken` JWT RS256 gerado com claims:
  ```json
  {
    "sub": "<userId>",
    "email": "ana@loja.com.br",
    "role": "MERCHANT_OWNER",
    "merchantId": "<uuid-ou-null>",
    "iat": 1748350860,
    "exp": 1748351760
  }
  ```
- `refreshToken` (UUID) gravado no Redis: `refresh_token:{userId}:{tokenId}` com TTL 7 dias
- `refreshToken` enviado em cookie `httpOnly; Secure; SameSite=Strict`
- Contador de tentativas falhas zerado no Redis
- Evento `user.login.success` publicado no Kafka

---

## 5. Pós-condições (Erro)

| Código | HTTP | Retryable | Descrição |
|--------|------|-----------|-----------|
| INVALID_CREDENTIALS | 401 | false | Email ou senha incorretos (mensagem genérica) |
| ACCOUNT_LOCKED | 423 | false | Bloqueada por tentativas excessivas; response inclui `unlockAt` |
| ACCOUNT_NOT_CONFIRMED | 403 | false | Email não confirmado |
| ACCOUNT_DISABLED | 403 | false | Conta desativada |
| TOO_MANY_REQUESTS | 429 | true | Rate limit excedido |

**Pós-condições de falha:**
- Contador de tentativas incrementado: `login_attempts:{email}` (TTL 30 min)
- Se tentativas ≥ 5: `account_locked:{email}` criado (TTL 30 min) + evento `user.login.blocked` no Kafka

---

## 6. Invariantes

1. `password` nunca aparece em logs ou responses
2. Mensagem de erro é genérica — não revela qual campo falhou
3. Tempo de resposta com senha errada ≈ senha certa (BCrypt é constant-time)
4. `refreshToken` somente em httpOnly cookie — nunca no body
5. Após 5 tentativas falhas consecutivas, conta bloqueada por 30 min
6. Token expira em exatamente 900 segundos (15 min)

---

## 7. Casos Extremos

| ID | Input | Comportamento | Output |
|----|-------|--------------|--------|
| CE-001 | 2FA ativo, code não enviado | Validar senha → retornar RequiresTwoFactor | 200 + requiresTwoFactor: true |
| CE-002 | 5ª tentativa falha | INVALID_CREDENTIALS + bloquear conta + Kafka | 401 + lock ativado |
| CE-003 | Login com conta bloqueada | ACCOUNT_LOCKED com `unlockAt` | 423 + unlockAt |
| CE-004 | Email não confirmado | ACCOUNT_NOT_CONFIRMED, sem revelar se senha certa | 403 |
| CE-005 | Email não cadastrado | INVALID_CREDENTIALS (mesmo código — não vazar existência) | 401 |

---

## 8. Exemplos Concretos

### Exemplo 1 — Sucesso (MERCHANT_OWNER sem 2FA)

**Request:**
```json
{ "email": "ana@loja.com.br", "password": "MinhaS3nha!" }
```

**Response HTTP 200:**
```json
{
  "accessToken": "eyJhbGciOiJSUzI1NiJ9...",
  "tokenType": "Bearer",
  "expiresIn": 900,
  "requiresTwoFactor": false
}
```

**Set-Cookie:** `refreshToken=<uuid>; HttpOnly; Secure; SameSite=Strict; Path=/api/v1/auth/refresh; Max-Age=604800`

### Exemplo 2 — 2FA requerido

**Response HTTP 200:**
```json
{
  "requiresTwoFactor": true,
  "twoFactorToken": "2fa_temp_token_uuid"
}
```

### Exemplo 3 — Credenciais inválidas

**Response HTTP 401:**
```json
{
  "errors": [{
    "code": "INVALID_CREDENTIALS",
    "message": "Email ou senha incorretos.",
    "retryable": false
  }]
}
```

---

## 9. Efeitos Colaterais

| Efeito | Quando | Obrigatório |
|--------|--------|-------------|
| Gravar refreshToken no Redis | Sucesso | Sim |
| Incrementar contador de falhas no Redis | Falha de credenciais | Sim |
| Criar lock no Redis | 5ª tentativa falha | Sim |
| Publicar `user.login.success` no Kafka | Sucesso | Best-effort |
| Publicar `user.login.blocked` no Kafka | Bloqueio | Best-effort |
| Email de alerta de segurança | Bloqueio (via Kafka) | Best-effort |

---

## 10. Performance

| Etapa | P50 | P99 |
|-------|-----|-----|
| BCrypt verify (rounds=12) | 60ms | 130ms |
| Redis (check lock + write token) | 5ms | 15ms |
| PostgreSQL read | 10ms | 30ms |
| Kafka publish | 5ms | 20ms |
| **Total** | **80ms** | **195ms** |

---

## 11. Segurança

- JWT assinado com **RS256** (RSA 2048 bits, chave assimétrica)
- `refreshToken` em httpOnly cookie — protege contra XSS
- BCrypt constant-time compare — previne timing attacks
- Rate limiting: 5 tentativas por email em janela de 30 min (Redis)
- Lock ativo não revela se email existe (mesma mensagem de erro)
- `password` nunca logado ou retornado em nenhum cenário
