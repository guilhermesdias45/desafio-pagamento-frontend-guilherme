# PRD: User Service — Acabou o Mony

## TDD Mode: REQUIRED

Cada task de implementação DEVE seguir o ciclo RED → GREEN → REFACTOR:
1. RED: Escrever teste falhando baseado na spec (cada CE-* = 1 teste mínimo)
2. GREEN: Implementar o mínimo para o teste passar
3. REFACTOR: Melhorar sem quebrar testes

Nomes de testes: `deve_<comportamento>_quando_<condição>()`
Jamais usar H2 — usar Testcontainers com PostgreSQL real.
Cobertura mínima: 90% por módulo (JaCoCo).

---

## Objetivo

Implementar o user-service completo: autenticação JWT RS256, registro de CUSTOMER e MERCHANT_OWNER (atômico), 2FA TOTP, refresh token com rotação, rate limiting Redis e endpoint interno para validação de token pelo api-gateway.

Stack: Java 21 + Spring Boot 3.x + PostgreSQL + Redis + Kafka. DTOs como Records. Resultados como sealed interfaces.

---

## Comportamento esperado

### Registro (POST /api/v1/auth/register)
- CUSTOMER: cria usuário com status PENDING_EMAIL_CONFIRMATION; emite Kafka user.registered
- MERCHANT_OWNER: cria usuário + merchant na mesma transação atômica; campos extras: companyName + cnpj (validação de formato + dígitos verificadores Módulo 11)
- STAFF não pode registrar via este endpoint (retorna INVALID_ROLE)
- Senha: BCrypt rounds=12; nunca logada

### Login (POST /api/v1/auth/login)
- Sucesso: retorna accessToken JWT RS256 (15 min) + refreshToken em httpOnly cookie
- Com 2FA ativo e sem totpCode: retorna requiresTwoFactor=true + twoFactorToken temporário (5 min)
- Rate limiting: 5 tentativas falhas → conta bloqueada 30 min via Redis
- Timing attack prevention: tempo de resposta uniforme independente do motivo da falha

### JWT Claims
```json
{
  "sub": "userId-uuid",
  "email": "user@example.com",
  "role": "MERCHANT_OWNER",
  "merchantId": "uuid-ou-null",
  "iat": 1748350860,
  "exp": 1748351760
}
```
Assinado com RS256; chave privada via env var JWT_PRIVATE_KEY (base64 PEM).

### Refresh Token (POST /api/v1/auth/refresh)
- Rotação obrigatória: token antigo deletado do Redis, novo gerado
- refreshToken via httpOnly cookie; nunca no body
- TTL Redis: 7 dias

### 2FA (TOTP — RFC 6238)
- Library: dev.samstevens.totp:1.7.x
- Setup: gera secret + QR code + 8 recovery codes
- Secret TOTP criptografado com AES-256-GCM no banco
- Recovery codes hasheados com BCrypt
- Tolerância: ±1 janela de 30s

### Endpoint Interno (POST /internal/auth/validate-token)
- Requer header X-Internal-Secret (valor via env var INTERNAL_SECRET)
- Sem o header ou com valor inválido: retorna 403 imediatamente
- Retorna: userId, email, role, merchantId
- P99 < 50ms

### Perfil (PATCH /api/v1/users/me)
- Sprint 1: apenas fullName

---

## Modelo de Roles

| Role | Pode comprar | Tem merchant | Criado via |
|------|-------------|--------------|------------|
| CUSTOMER | Sim | Não | Registro direto |
| MERCHANT_OWNER | Sim | Sim (atômico no registro) | Registro direto |
| STAFF | Não | Sim | Convite (Sprint 2 — não implementar agora) |

---

## Casos de erro

| Código | HTTP | Descrição |
|--------|------|-----------|
| EMAIL_ALREADY_EXISTS | 409 | Email já cadastrado |
| WEAK_PASSWORD | 400 | Senha não atende critérios |
| INVALID_ROLE | 400 | Role não permitida neste endpoint |
| INVALID_CNPJ | 400 | CNPJ inválido |
| MISSING_MERCHANT_DATA | 400 | companyName/cnpj ausentes para MERCHANT_OWNER |
| CNPJ_ALREADY_REGISTERED | 409 | CNPJ duplicado |
| INVALID_CREDENTIALS | 401 | Email ou senha incorretos (mensagem genérica) |
| ACCOUNT_LOCKED | 423 | Conta bloqueada (inclui unlockAt ISO 8601) |
| ACCOUNT_NOT_CONFIRMED | 403 | Email não confirmado |
| INVALID_TOTP_CODE | 401 | Código 2FA inválido |
| REFRESH_TOKEN_INVALID | 401 | Refresh token inválido ou expirado |
| TWO_FACTOR_ALREADY_ENABLED | 409 | 2FA já ativo |
| RECOVERY_CODE_INVALID | 401 | Recovery code inválido ou já usado |

---

## Casos extremos (obrigatório: 1 teste por CE)

- CE-REG-001: Email duplicado → 409
- CE-REG-002: role=MERCHANT_OWNER sem cnpj → 400 MISSING_MERCHANT_DATA
- CE-REG-003: CNPJ com formato inválido → 400 INVALID_CNPJ
- CE-REG-004: CNPJ com dígitos verificadores errados → 400 INVALID_CNPJ
- CE-REG-005: CNPJ já cadastrado → 409 CNPJ_ALREADY_REGISTERED
- CE-REG-006: role=STAFF no registro → 400 INVALID_ROLE
- CE-REG-007: Falha ao criar merchant → usuário NÃO criado (transação atômica)
- CE-LOGIN-001: 5ª tentativa falha → bloqueia conta + emite Kafka user.login.blocked
- CE-LOGIN-002: Login com conta bloqueada → 423 com unlockAt
- CE-LOGIN-003: Email não confirmado → 403 sem revelar se senha estava certa
- CE-LOGIN-004: 2FA ativo sem totpCode → retorna requiresTwoFactor=true
- CE-REFRESH-001: Token já usado (após rotação) → 401
- CE-2FA-001: Todos os 8 recovery codes usados → RECOVERY_CODE_EXHAUSTED
- CE-INTERNAL-001: Requisição sem X-Internal-Secret → 403
- CE-INTERNAL-002: X-Internal-Secret inválido → 403

---

## Efeitos colaterais esperados

- **Kafka (tópico `user-events`):**
  - `user.registered` — ao criar conta (inclui merchantId para MERCHANT_OWNER)
  - `user.login.blocked` — ao bloquear conta
  - `user.2fa.enabled` — ao ativar 2FA
  - `user.login.success` — ao logar com sucesso
- **Redis:** refresh_token, login_attempts, account_locked, email_confirm, 2fa_setup, 2fa_login
- **Flyway:** V1 (users), V2 (merchants + ALTER TABLE), V3 (recovery_codes), V4 (audit_logs)

---

## Restrições técnicas

- Java 21: Records para DTOs, sealed interfaces para AuthResult, Virtual Threads para I/O
- Spring Boot 3.x + Spring Security 6
- PostgreSQL 16 (Testcontainers — nunca H2)
- Redis para rate limiting, refresh tokens e confirmação de email
- Kafka KRaft (bitnami/kafka:3.7) para eventos
- JWT: biblioteca JJWT (io.jsonwebtoken), RS256
- TOTP: dev.samstevens.totp:1.7.x
- Cobertura: JaCoCo ≥ 90%
- Logs: nunca logar password, cnpj, totpCode, refreshToken, cardNumber
- Erros: RFC 7807 Problem Details

## Segurança

- Autenticação: JWT RS256 obrigatório em /users/** e /auth/logout
- Rotas públicas: /auth/register, /auth/confirm-email, /auth/login, /auth/refresh
- Endpoint interno: /internal/** → apenas com X-Internal-Secret header correto
- Dados sensíveis nunca logados: password, cnpj, totpCode, refreshToken
- Rate limiting por email (não por IP) via Redis

## Performance

- Register: P50 80ms, P99 200ms
- Login: P50 150ms, P99 300ms
- Token validate: P50 10ms, P99 50ms
- Refresh: P50 20ms, P99 80ms
