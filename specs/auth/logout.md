# Especificação: Logout

**ID:** SPEC-AUTH-003  
**Serviço:** user-service  
**Status:** Aprovado  
**Revisores:** [x] PM [x] Arquiteto [x] QA [x] Security

---

## 1. Assinatura

```java
POST /api/v1/auth/logout
Authorization: Bearer {accessToken}
Cookie: refreshToken={uuid}

// Service
public void logout(UUID userId, String refreshToken, String accessToken, Long tokenExp)
```

---

## 2. Tipos de Dados

### Input

| Fonte | Campo | Tipo | Obrigatório |
|-------|-------|------|-------------|
| Header `Authorization` | accessToken | String (JWT) | Sim |
| Cookie `refreshToken` | refreshToken | String (UUID) | Sim |

### Output — Sucesso (HTTP 204 No Content)

Sem body.

---

## 3. Pré-condições

- `accessToken` presente no header `Authorization: Bearer`
- `refreshToken` presente no cookie httpOnly
- Usuário autenticado (JWT válido ou expirado — logout aceita ambos)

---

## 4. Pós-condições (Sucesso)

1. `refreshToken` deletado do Redis: `refresh_token:{userId}:{tokenId}`
2. `accessToken` adicionado à blacklist no Redis: `blacklist:{token}` com TTL = tempo restante de expiração do JWT
3. Cache de validação do gateway invalidado: `token_validation:{sha256(token)}` deletado do Redis — garante revogação **imediata** (sem esperar os 30s de cache do gateway)

---

## 5. Pós-condições (Erro)

| Código | HTTP | Retryable | Descrição |
|--------|------|-----------|-----------|
| MISSING_TOKEN | 401 | false | accessToken ausente no header |
| REFRESH_TOKEN_MISSING | 400 | false | Cookie refreshToken ausente |

> Logout é idempotente: se o token já estiver na blacklist ou o refreshToken já tiver sido deletado, retorna 204 mesmo assim.

---

## 6. Invariantes

1. Após logout bem-sucedido, `accessToken` é **imediatamente** inválido em todos os serviços
2. O cache do gateway (`token_validation`) é deletado no mesmo momento da blacklist — sem janela de 30s
3. `accessToken` continua na blacklist até seu `exp` natural — não gera lixo permanente
4. Logout nunca falha silenciosamente — se Redis estiver down, retorna 503

---

## 7. Casos Extremos

| ID | Input | Comportamento | Output |
|----|-------|--------------|--------|
| CE-001 | Token já na blacklist (logout duplo) | Idempotente — retornar 204 | 204 No Content |
| CE-002 | refreshToken já expirado/deletado | Ainda blacklista o accessToken | 204 No Content |
| CE-003 | Token JWT expirado mas logout solicitado | Blacklista de qualquer forma (TTL=0 é ignorado) | 204 No Content |
| CE-004 | Redis indisponível | Retornar 503 — não fingir sucesso | 503 SERVICE_UNAVAILABLE |

---

## 8. Exemplos Concretos

### Exemplo 1 — Sucesso

**Request:**
```http
POST /api/v1/auth/logout
Authorization: Bearer eyJhbGciOiJSUzI1NiJ9...
Cookie: refreshToken=a1b2c3d4-e5f6-7890-abcd-ef1234567890
```

**Response HTTP 204** — sem body.

**Efeitos no Redis:**
- `refresh_token:{userId}:{tokenId}` → DELETED
- `blacklist:eyJhbGciOiJSUzI1NiJ9...` → SET com TTL = segundos restantes do JWT
- `token_validation:{sha256(token)}` → DELETED

### Exemplo 2 — Acesso após logout

```http
GET /api/v1/users/me
Authorization: Bearer eyJhbGciOiJSUzI1NiJ9...  ← mesmo token
```

**Response HTTP 401:**
```json
{
  "errors": [{ "code": "TOKEN_REVOKED", "message": "Token inválido.", "retryable": false }]
}
```

---

## 9. Efeitos Colaterais

| Efeito | Síncrono/Assíncrono | Obrigatório |
|--------|---------------------|-------------|
| Deletar refreshToken do Redis | Síncrono | Sim |
| Blacklistar accessToken no Redis | Síncrono | Sim |
| Deletar cache de validação do gateway | Síncrono | Sim |

---

## 10. Performance

| Etapa | P50 | P99 |
|-------|-----|-----|
| Redis DEL (3 operações) | 5ms | 15ms |
| **Total** | **5ms** | **15ms** |

---

## 11. Segurança

- Revogação **imediata** do token (sem janela de cache de 30s do gateway)
- `sha256` do token usado como chave de cache — não armazena o token completo em log
- TTL automático na blacklist — sem acúmulo de dados obsoletos
- Cookie `refreshToken` limpo com `Max-Age=0` na resposta
