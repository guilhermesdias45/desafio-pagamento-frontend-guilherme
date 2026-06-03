# Especificação: GET /internal/users/{customerId}

## 1. Assinatura

```
GET /internal/users/{customerId}
Header: X-Internal-Secret: <secret>
Path:   customerId — UUID do usuário a consultar
```

Resposta de sucesso (`200 OK`):
```json
{
  "id":    "uuid",
  "email": "string",
  "role":  "CUSTOMER | MERCHANT_OWNER | ADMIN"
}
```

## 2. Tipos de Dados

| Campo | Tipo | Origem |
|-------|------|--------|
| `customerId` | `UUID` (path) | Fornecido pelo chamador (payment-service) |
| `X-Internal-Secret` | `String` (header) | Validado pelo `InternalSecretFilter` existente |
| `id` | `UUID` | `User.id` |
| `email` | `String` | `User.email` |
| `role` | `String` | `User.role.name()` |

## 3. Pré-condições

- `X-Internal-Secret` presente e válido (validação feita pelo `InternalSecretFilter` antes de chegar ao controller)
- `customerId` é um UUID bem-formado

## 4. Pós-condições (Sucesso)

- Retorna `200 OK` com `{ id, email, role }` do usuário identificado por `customerId`
- Nenhum efeito colateral (operação de leitura pura)
- Campos sensíveis (`passwordHash`, `totpSecretEncrypted`) nunca expostos na resposta

## 5. Pós-condições (Erro)

| Condição | HTTP | errorCode |
|----------|------|-----------|
| `X-Internal-Secret` ausente ou inválido | 401 | `UNAUTHORIZED` (InternalSecretFilter — já existente) |
| `customerId` não é UUID válido | 400 | `INVALID_UUID` (GlobalExceptionHandler — já existente) |
| Usuário não encontrado no banco | 404 | `USER_NOT_FOUND` |

## 6. Invariantes

- Nenhum dado sensível exposto: apenas `id`, `email`, `role`
- Endpoint jamais retorna 200 com corpo vazio — sempre 200+corpo ou 404
- `InternalSecretFilter` rejeita a requisição com 401 antes de chegar ao controller se o secret for inválido

## 7. Casos Extremos

| ID | Input | Comportamento esperado | Output |
|----|-------|------------------------|--------|
| CE-001 | `customerId` válido, usuário existe | Retorna dados do usuário | `200 { id, email, role }` |
| CE-002 | `customerId` válido, usuário **não** existe | Não lança exceção, retorna 404 | `404 USER_NOT_FOUND` |
| CE-003 | `X-Internal-Secret` ausente | Rejeitado pelo filter antes do controller | `401 UNAUTHORIZED` |
| CE-004 | `X-Internal-Secret` incorreto | Rejeitado pelo filter antes do controller | `401 UNAUTHORIZED` |
| CE-005 | `customerId` não é UUID (ex: `"abc"`) | `MethodArgumentTypeMismatchException` → GlobalExceptionHandler | `400 INVALID_UUID` |
| CE-006 | Usuário existe mas está `BLOCKED` | Retorna normalmente — payment-service decide como interpretar o status | `200 { id, email, role }` |

## 8. Exemplos Concretos

**Sucesso:**
```
GET /internal/users/f47ac10b-58cc-4372-a567-0e02b2c3d479
X-Internal-Secret: dev-secret

→ 200 OK
{
  "id":    "f47ac10b-58cc-4372-a567-0e02b2c3d479",
  "email": "ana@loja.com",
  "role":  "CUSTOMER"
}
```

**Usuário não encontrado:**
```
GET /internal/users/00000000-0000-0000-0000-000000000000
X-Internal-Secret: dev-secret

→ 404 Not Found
{
  "type":   "about:blank",
  "title":  "Not Found",
  "status": 404,
  "detail": "USER_NOT_FOUND"
}
```

## 9. Efeitos Colaterais

Nenhum. Operação de leitura pura. Sem Kafka, Redis, email ou audit log.

## 10. Performance

- **P50:** < 15 ms (leitura simples por PK no PostgreSQL)
- **P99:** < 50 ms
- Sem cache Redis — payment-service tem o próprio circuit breaker com timeout de 300 ms

## 11. Segurança

- Protegido exclusivamente pelo `InternalSecretFilter` (header `X-Internal-Secret`) — sem JWT
- Rota `/internal/**` já está configurada como `permitAll()` no `SecurityConfig` mas o filter valida o secret antes
- Nunca logar: `email` completo, `passwordHash`, `totpSecretEncrypted`
- Logar apenas: `customerId` (para rastreabilidade), status HTTP da resposta
- PCI DSS: endpoint retorna apenas dados de identificação, sem dados de cartão ou financeiros
