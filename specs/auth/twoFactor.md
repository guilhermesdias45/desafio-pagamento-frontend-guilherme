# Especificação: Autenticação de Dois Fatores (2FA)

**ID:** SPEC-AUTH-005  
**Serviço:** user-service  
**Status:** Aprovado  
**Revisores:** [x] PM [x] Arquiteto [x] QA [x] Security

---

## 1. Assinatura

```java
// Setup — iniciar configuração 2FA
POST /api/v1/auth/2fa/setup
Authorization: Bearer {jwt}

// Confirm — ativar 2FA com primeiro código gerado
POST /api/v1/auth/2fa/confirm
Authorization: Bearer {jwt}
Body: { "totpCode": "123456" }

// Verify — verificar código durante login
POST /api/v1/auth/2fa/verify
Body: { "twoFactorToken": "...", "totpCode": "123456" }

// Disable — desativar 2FA
POST /api/v1/auth/2fa/disable
Authorization: Bearer {jwt}
Body: { "password": "...", "totpCode": "123456" }

// Recovery — usar recovery code de emergência
POST /api/v1/auth/2fa/recovery
Body: { "twoFactorToken": "...", "recoveryCode": "XXXX-XXXX" }
```

---

## 2. Tipos de Dados

### Output — Setup (HTTP 200)

```java
public record TwoFactorSetupResponse(
    String secret,        // Base32 TOTP secret — exibido apenas uma vez
    String qrCodeUrl,     // data:image/png;base64,...
    String otpAuthUrl,    // otpauth://totp/...
    List<String> recoveryCodes  // 8 códigos únicos — exibidos apenas uma vez
) {}
```

### Input — Confirm

```java
public record TwoFactorConfirmRequest(
    @NotBlank @Size(min = 6, max = 6) String totpCode
) {}
```

### Input — Verify

```java
public record TwoFactorVerifyRequest(
    @NotBlank String twoFactorToken,
    @NotBlank @Size(min = 6, max = 6) String totpCode
) {}
```

---

## 3. Pré-condições

- **Setup:** usuário autenticado; 2FA não está ativo (`twoFactorEnabled = false`)
- **Confirm:** usuário autenticado; setup iniciado (secret temporário no Redis); código TOTP gerado pelo app do usuário
- **Verify:** `twoFactorToken` válido no Redis (TTL 5 min); código TOTP dentro da janela de tempo
- **Disable:** usuário autenticado; 2FA ativo; senha e código TOTP corretos
- **Recovery:** `twoFactorToken` válido; recovery code válido e não usado

---

## 4. Pós-condições (Sucesso)

### Setup
- Secret TOTP temporário gravado no Redis: `2fa_setup:{userId}` com TTL 10 min
- 8 recovery codes gerados (únicos, one-time-use)

### Confirm (ativar 2FA)
- `twoFactorEnabled = true` na conta
- Secret TOTP criptografado com **AES-256-GCM** e gravado no banco
- 8 recovery codes hasheados com BCrypt gravados na tabela `recovery_codes`
- Secret temporário deletado do Redis
- Evento `user.2fa.enabled` publicado no Kafka

### Verify (login com 2FA)
- `twoFactorToken` deletado do Redis
- Sessão completa iniciada: accessToken JWT + refreshToken cookie (mesmo fluxo do login normal)

### Disable
- `twoFactorEnabled = false` na conta
- Secret TOTP deletado do banco
- Recovery codes deletados

---

## 5. Pós-condições (Erro)

| Código | HTTP | Retryable | Operação |
|--------|------|-----------|----------|
| TWO_FACTOR_ALREADY_ENABLED | 409 | false | Setup |
| INVALID_TOTP_CODE | 401 | false | Confirm, Verify, Disable |
| TWO_FACTOR_NOT_ENABLED | 422 | false | Disable |
| RECOVERY_CODE_INVALID | 401 | false | Recovery |
| RECOVERY_CODE_EXHAUSTED | 422 | false | Recovery |
| TWO_FACTOR_TOKEN_EXPIRED | 401 | false | Verify, Recovery |

---

## 6. Invariantes

1. Secret TOTP **nunca** exibido após o setup inicial (apenas no momento do setup)
2. Recovery codes exibidos **apenas uma vez** (no momento do setup)
3. Cada recovery code pode ser usado **uma única vez**
4. Quando todos os 8 recovery codes são usados → novo setup obrigatório
5. Tolerância de tempo: ±1 janela de 30s (RFC 6238)
6. Secret TOTP armazenado criptografado com AES-256-GCM — nunca em texto puro

---

## 7. Casos Extremos

| ID | Input | Comportamento | Output |
|----|-------|--------------|--------|
| CE-001 | Setup com 2FA já ativo | Rejeitar | 409 TWO_FACTOR_ALREADY_ENABLED |
| CE-002 | Código fora da janela de 30s (±1 janela de tolerância) | Aceitar se dentro de ±1 janela | Depende |
| CE-003 | Recovery code já usado | Rejeitar | 401 RECOVERY_CODE_INVALID |
| CE-004 | Todos os 8 recovery codes usados | Exigir novo setup 2FA | 422 RECOVERY_CODE_EXHAUSTED |
| CE-005 | twoFactorToken expirado (> 5 min) | Rejeitar, forçar novo login | 401 TWO_FACTOR_TOKEN_EXPIRED |
| CE-006 | Disable com código errado | Rejeitar sem revelar qual campo falhou | 401 INVALID_TOTP_CODE |

---

## 8. Exemplos Concretos

### Exemplo 1 — Setup bem-sucedido

**Response HTTP 200:**
```json
{
  "secret": "JBSWY3DPEHPK3PXP",
  "qrCodeUrl": "data:image/png;base64,iVBORw0KGgo...",
  "otpAuthUrl": "otpauth://totp/AcabouoMony:ana@loja.com.br?secret=JBSWY3DPEHPK3PXP&issuer=AcabouoMony",
  "recoveryCodes": [
    "ABCD-1234", "EFGH-5678", "IJKL-9012",
    "MNOP-3456", "QRST-7890", "UVWX-1234",
    "YZAB-5678", "CDEF-9012"
  ]
}
```

### Exemplo 2 — Login com 2FA (verify)

**Request:**
```json
{ "twoFactorToken": "2fa_temp_uuid", "totpCode": "123456" }
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

---

## 9. Efeitos Colaterais

| Efeito | Quando | Obrigatório |
|--------|--------|-------------|
| Gravar secret temporário no Redis | Setup | Sim |
| Gravar secret AES-256-GCM no banco | Confirm | Sim |
| Gravar recovery codes BCrypt no banco | Confirm | Sim |
| Publicar `user.2fa.enabled` no Kafka | Confirm | Best-effort |
| Deletar twoFactorToken do Redis | Verify/Recovery | Sim |
| Marcar recovery code como usado | Recovery | Sim |

---

## 10. Performance

| Operação | P50 | P99 |
|----------|-----|-----|
| Setup (gerar QR) | 20ms | 80ms |
| Confirm (BCrypt 8 codes) | 80ms | 200ms |
| Verify (TOTP check) | 10ms | 40ms |
| Recovery (BCrypt verify) | 60ms | 150ms |

---

## 11. Segurança

- TOTP usa HMAC-SHA1, período 30s, 6 dígitos (RFC 6238)
- Secret TOTP criptografado com **AES-256-GCM** (inclui MAC — mais seguro que CBC)
- Recovery codes hasheados com BCrypt antes de armazenar
- `twoFactorToken` tem TTL de 5 min — janela de tempo limitada para ataques de força bruta
- Secret exibido **apenas no momento do setup** — não há endpoint para recuperá-lo depois
