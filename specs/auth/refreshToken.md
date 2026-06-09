# Especificação: Refresh Token

**ID:** SPEC-AUTH-004  
**Serviço:** user-service  
**Status:** Aprovado  
**Revisores:** [x] PM [x] Arquiteto [x] QA [x] Security

---

## 1. Assinatura

```java
POST /api/v1/auth/refresh
Cookie: refreshToken={uuid}

// Service
public AuthResult.Success refresh(String refreshTokenValue)
```

---

## 2. Tipos de Dados

### Input

| Fonte | Campo | Tipo | Obrigatório |
|-------|-------|------|-------------|
| Cookie `refreshToken` | refreshToken | String (UUID opaque) | Sim |

### Output — Sucesso (HTTP 200)

```java
public record RefreshResponse(
    String accessToken,   // novo JWT RS256, expira em 900s
    String tokenType,     // "Bearer"
    int expiresIn         // 900
) {}
```

Novo `refreshToken` enviado no `Set-Cookie` httpOnly (rotação).

---

## 3. Pré-condições

- Cookie `refreshToken` presente
- Token encontrado no Redis: `refresh_token:{userId}:{tokenId}`
- Token não expirado (TTL > 0 no Redis)

---

## 4. Pós-condições (Sucesso)

1. Token antigo **deletado imediatamente** do Redis (rotação obrigatória)
2. Novo `accessToken` JWT RS256 gerado com claims atualizados
3. Novo `refreshToken` UUID gerado e gravado no Redis (TTL 7 dias)
4. Novo `refreshToken` enviado em cookie `httpOnly; Secure; SameSite=Strict`

---

## 5. Pós-condições (Erro)

| Código | HTTP | Retryable | Descrição |
|--------|------|-----------|-----------|
| REFRESH_TOKEN_MISSING | 400 | false | Cookie ausente |
| REFRESH_TOKEN_INVALID | 401 | false | Token não encontrado no Redis |
| REFRESH_TOKEN_EXPIRED | 401 | false | TTL expirado no Redis |

---

## 6. Invariantes

1. O token antigo é **sempre deletado** antes de emitir o novo (nunca dois tokens válidos ao mesmo tempo)
2. Token roubado usado após renovação pelo usuário legítimo retorna 401 (já foi deletado na etapa 1)
3. Cada refreshToken tem uso único — não pode ser reutilizado

---

## 7. Casos Extremos

| ID | Input | Comportamento | Output |
|----|-------|--------------|--------|
| CE-001 | Token roubado usado após rotação | Token já deletado → inválido | 401 REFRESH_TOKEN_INVALID |
| CE-002 | Cookie ausente | Rejeitar imediatamente | 400 REFRESH_TOKEN_MISSING |
| CE-003 | Token expirado (7 dias) | Rejeitar, forçar novo login | 401 REFRESH_TOKEN_EXPIRED |
| CE-004 | Dois clientes com o mesmo token (race condition) | Primeiro wins; segundo recebe 401 | 401 REFRESH_TOKEN_INVALID |

---

## 8. Exemplos Concretos

### Exemplo 1 — Sucesso

**Request:**
```http
POST /api/v1/auth/refresh
Cookie: refreshToken=a1b2c3d4-e5f6-7890-abcd-ef1234567890
```

**Response HTTP 200:**
```json
{
  "accessToken": "eyJhbGciOiJSUzI1NiJ9...",
  "tokenType": "Bearer",
  "expiresIn": 900
}
```

**Set-Cookie:** `refreshToken=<new-uuid>; HttpOnly; Secure; SameSite=Strict; Path=/api/v1/auth/refresh; Max-Age=604800`

### Exemplo 2 — Token inválido

**Response HTTP 401:**
```json
{
  "errors": [{ "code": "REFRESH_TOKEN_INVALID", "message": "Sessão expirada. Faça login novamente.", "retryable": false }]
}
```

---

## 9. Efeitos Colaterais

| Efeito | Síncrono/Assíncrono | Obrigatório |
|--------|---------------------|-------------|
| Deletar refreshToken antigo do Redis | Síncrono | Sim |
| Gravar novo refreshToken no Redis | Síncrono | Sim |

---

## 10. Performance

| Etapa | P50 | P99 |
|-------|-----|-----|
| Redis GET + DEL + SET | 8ms | 20ms |
| Gerar JWT | 2ms | 5ms |
| **Total** | **10ms** | **25ms** |

---

## 11. Segurança

- Rotação obrigatória em cada uso — invalida tokens vazados automaticamente
- TTL 7 dias no Redis — sem tokens "eternos"
- Token opaque (UUID) — não contém claims, não pode ser decodificado
- Armazenado em cookie `httpOnly` — protege contra XSS
