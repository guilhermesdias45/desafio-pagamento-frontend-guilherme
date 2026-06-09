# Especificação: Registrar Usuário

**ID:** SPEC-AUTH-001  
**Serviço:** user-service  
**Status:** Aprovado  
**Revisores:** [x] PM [x] Arquiteto [x] QA [x] Security

---

## 1. Assinatura

```java
// Controller
POST /api/v1/auth/register
Content-Type: application/json
Body: RegisterRequest

// Service
public RegisterResult register(RegisterRequest request)
```

---

## 2. Tipos de Dados

### Input — RegisterRequest

```java
public record RegisterRequest(
    @NotBlank @Email @Size(max = 255) String email,
    @NotBlank @Size(min = 8, max = 100) String password,
    @NotBlank @Size(min = 2, max = 100) String fullName,
    @NotNull UserRole role,
    @Size(max = 100) String companyName,
    @Size(min = 14, max = 14) String cnpj
) {}
```

**Validação condicional:**
- Se `role = MERCHANT_OWNER`: `companyName` e `cnpj` são obrigatórios
- Se `role = CUSTOMER`: `companyName` e `cnpj` são ignorados
- `role = STAFF` não é aceito — retorna `INVALID_ROLE`

### Output — Sucesso (HTTP 201)

```java
public record RegisterResponse(
    UUID userId,
    String email,
    String role,
    UUID merchantId,       // null para CUSTOMER
    boolean emailConfirmed // sempre false
) {}
```

---

## 3. Pré-condições

- `email` com formato válido e não duplicado no banco
- `password` com pelo menos 8 chars, 1 maiúscula, 1 número, 1 especial
- `role` é `CUSTOMER` ou `MERCHANT_OWNER`
- Se `role = MERCHANT_OWNER`: `cnpj` com 14 dígitos e dígitos verificadores válidos (Módulo 11); `cnpj` não cadastrado

---

## 4. Pós-condições (Sucesso)

**Para CUSTOMER:**
- Usuário gravado com `status = PENDING_EMAIL_CONFIRMATION`, `role = CUSTOMER`, `merchant_id = null`
- Token de confirmação (UUID) criado no Redis: `confirm_email:{userId}` com TTL 24h
- Evento `user.registered` publicado no Kafka

**Para MERCHANT_OWNER:**
- Usuário e merchant criados em **transação atômica única**
- Merchant gravado com `owner_id = userId`
- `merchant_id` FK preenchido no registro do usuário
- Evento `user.registered` publicado no Kafka (inclui `merchantId`)

**Ambos:**
- Senha armazenada com BCrypt (rounds = 12)
- notification-service consome `user.registered` e envia email de boas-vindas + link de confirmação

---

## 5. Pós-condições (Erro)

| Código | HTTP | Retryable | Descrição |
|--------|------|-----------|-----------|
| EMAIL_ALREADY_EXISTS | 409 | false | E-mail já cadastrado |
| WEAK_PASSWORD | 400 | false | Senha não atende critérios mínimos |
| INVALID_ROLE | 400 | false | Role não permitida para este endpoint |
| INVALID_CNPJ | 400 | false | CNPJ inválido (formato ou dígitos verificadores) |
| MISSING_MERCHANT_DATA | 400 | false | companyName ou cnpj ausentes para MERCHANT_OWNER |
| CNPJ_ALREADY_REGISTERED | 409 | false | CNPJ já cadastrado |

---

## 6. Invariantes

1. `password` nunca é logado, armazenado em texto puro ou retornado
2. Se criação do merchant falhar, o usuário **não** é criado (transação atômica)
3. `cnpj` nunca aparece em logs
4. `emailConfirmed` é sempre `false` neste endpoint
5. Dois registros com o mesmo email nunca coexistem

---

## 7. Casos Extremos

| ID | Input | Comportamento | Output |
|----|-------|--------------|--------|
| CE-001 | Email duplicado | Rejeitar com mensagem genérica | 409 EMAIL_ALREADY_EXISTS |
| CE-002 | MERCHANT_OWNER sem cnpj | Rejeitar antes de qualquer I/O | 400 MISSING_MERCHANT_DATA |
| CE-003 | CNPJ inválido (formato) | Rejeitar com erro específico | 400 INVALID_CNPJ |
| CE-004 | CNPJ já cadastrado | Rejeitar | 409 CNPJ_ALREADY_REGISTERED |
| CE-005 | role = STAFF | Rejeitar imediatamente | 400 INVALID_ROLE |
| CE-006 | Falha ao publicar no Kafka após gravar usuário | Log de erro; não reverter o registro | 201 (Kafka é best-effort) |

---

## 8. Exemplos Concretos

### Exemplo 1 — Sucesso (MERCHANT_OWNER)

**Request:**
```json
{
  "email": "ana@roupasdaana.com.br",
  "password": "MinhaS3nha!",
  "fullName": "Ana Silva",
  "role": "MERCHANT_OWNER",
  "companyName": "Roupas da Ana LTDA",
  "cnpj": "11222333000181"
}
```

**Response HTTP 201:**
```json
{
  "data": {
    "userId": "550e8400-e29b-41d4-a716-446655440000",
    "email": "ana@roupasdaana.com.br",
    "role": "MERCHANT_OWNER",
    "merchantId": "763ab5c5-fd27-43d9-888c-5f1544c2ab64",
    "emailConfirmed": false
  }
}
```

**Efeitos colaterais:**
- `users` → 1 registro inserido
- `merchants` → 1 registro inserido
- Redis → `confirm_email:{userId}` com TTL 24h
- Kafka → `user.registered` publicado

### Exemplo 2 — Falha (email duplicado)

**Response HTTP 409:**
```json
{
  "errors": [{
    "code": "EMAIL_ALREADY_EXISTS",
    "message": "Este email já está cadastrado.",
    "retryable": false
  }]
}
```

---

## 9. Efeitos Colaterais

| Efeito | Quando | Obrigatório |
|--------|--------|-------------|
| Gravar usuário no PostgreSQL | Sempre (sucesso) | Sim |
| Gravar merchant no PostgreSQL | MERCHANT_OWNER | Sim (atômico) |
| Gravar token de confirmação no Redis | Sempre (sucesso) | Sim |
| Publicar `user.registered` no Kafka | Sempre (sucesso) | Best-effort |
| Email de boas-vindas | Após Kafka (async) | Não (falha silenciosa) |

---

## 10. Performance

| Etapa | P50 | P99 |
|-------|-----|-----|
| Validação | 5ms | 15ms |
| BCrypt hash (rounds=12) | 60ms | 120ms |
| PostgreSQL write (CUSTOMER) | 20ms | 60ms |
| PostgreSQL write (MERCHANT_OWNER) | 30ms | 80ms |
| Redis token | 5ms | 15ms |
| Kafka publish | 5ms | 20ms |
| **Total CUSTOMER** | **95ms** | **230ms** |
| **Total MERCHANT_OWNER** | **105ms** | **255ms** |

---

## 11. Segurança

- Senha armazenada com **BCrypt rounds=12** — nunca em texto puro
- Mensagem de erro do EMAIL_ALREADY_EXISTS é genérica (timing attack prevention)
- `cnpj` e `password` nunca aparecem em logs
- CNPJ validado com algoritmo Módulo 11 (sem consulta à Receita Federal)
- `emailConfirmed = false` — conta não pode fazer login até confirmar email
