# Especificação: Consultar Transação

**ID:** SPEC-PAY-003  
**Serviço:** payment-service  
**Status:** Aprovado  
**Revisores:** [x] PM [x] Arquiteto [x] QA [x] Security

---

## 1. Assinatura

```java
// Por ID
GET /api/v1/transactions/{transactionId}
Headers: X-Merchant-Id: {uuid}

// Listagem paginada
GET /api/v1/transactions?customerId={uuid}&status={status}&page={n}&size={n}&sort=createdAt,desc
Headers: X-Merchant-Id: {uuid}

// Service
public Optional<TransactionResponse> findById(String transactionId, UUID merchantId)
public Page<TransactionSummary> findByCustomer(UUID customerId, UUID merchantId, Pageable pageable)
public Page<TransactionSummary> findByCustomerAndStatus(UUID customerId, UUID merchantId, TransactionStatus status, Pageable pageable)
```

---

## 2. Tipos de Dados

### Output — TransactionResponse (GET por ID)

| Campo | Tipo | Descrição |
|-------|------|-----------|
| transactionId | String | ID interno |
| mpPaymentId | Long | ID no Mercado Pago |
| status | String | APPROVED, DECLINED, SUSPECTED_FRAUD, FULLY_REFUNDED, PARTIALLY_REFUNDED |
| amountInCents | Long | Valor em centavos |
| currency | String | "BRL" |
| paymentMethodId | String | visa, master, elo |
| orderId | UUID | Pedido associado |
| customerId | UUID | Cliente |
| merchantId | UUID | Merchant |
| processingTimeMs | Long | Tempo de processamento original |
| createdAt | Instant | Data/hora UTC |
| updatedAt | Instant | Última atualização |

**Campos NUNCA retornados:** número completo do cartão, CVV, cardToken.

### Output — TransactionSummary (listagem)

Subconjunto de TransactionResponse com: `transactionId`, `status`, `amountInCents`, `paymentMethodId`, `orderId`, `createdAt`.

### Output — Paginação

```json
{
  "data": [...],
  "meta": {
    "timestamp": "...",
    "page": 0,
    "size": 20,
    "pageSize": 20,
    "total": 150
  }
}
```

---

## 3. Pré-condições

- `merchantId` extraído do header `X-Merchant-Id`
- Para GET por ID: `transactionId` é um ID válido no formato `txn_*`
- Para listagem: `customerId` é um UUID válido; `size` ≤ 100

---

## 4. Pós-condições (Sucesso)

- GET por ID: retorna transação completa ou HTTP 403 se merchant não é dono
- Listagem: retorna página com transações filtradas por `customerId` e `merchantId`

---

## 5. Pós-condições (Erro)

| Situação | HTTP | Código |
|----------|------|--------|
| transactionId não encontrado ou merchant errado | 403 | INSUFFICIENT_PERMISSIONS |
| Parâmetro `status` inválido | 400 | — |

> Retornar 403 (não 404) quando transação não existe ou pertence a outro merchant — não revela existência.

---

## 6. Invariantes

1. Merchant vê apenas transações do próprio `merchantId` — sem vazamento entre merchants
2. GET por ID retorna 403 quando transação não pertence ao merchant (nunca 404)
3. Listagem filtra automaticamente por `merchantId` — nunca expõe dados de outros merchants
4. `size > 100` é silenciosamente truncado para 100

---

## 7. Casos Extremos

| ID | Input | Comportamento | Output |
|----|-------|--------------|--------|
| CE-001 | transactionId de outro merchant | 403 (não 404) | 403 INSUFFICIENT_PERMISSIONS |
| CE-002 | size = 500 | Truncar para 100 | 200 com max 100 itens |
| CE-003 | status inválido na query | Erro de validação | 400 |
| CE-004 | customerId sem transações | Retornar lista vazia | 200 + data: [] |

---

## 8. Exemplos Concretos

### Exemplo 1 — GET por ID (sucesso)

**Request:**
```http
GET /api/v1/transactions/txn_abc123
X-Merchant-Id: 7c9e6679-7425-40de-944b-e07fc1f90ae7
```

**Response HTTP 200:**
```json
{
  "data": {
    "transactionId": "txn_abc123",
    "mpPaymentId": 1234567890,
    "status": "APPROVED",
    "amountInCents": 8990,
    "currency": "BRL",
    "paymentMethodId": "visa",
    "orderId": "660f9511-f30c-52e5-b827-557766551111",
    "processingTimeMs": 547,
    "createdAt": "2026-06-09T14:00:00Z"
  },
  "meta": { "timestamp": "2026-06-09T14:01:00Z" }
}
```

### Exemplo 2 — Listagem paginada

**Request:**
```http
GET /api/v1/transactions?customerId=550e8400-e29b-41d4-a716-446655440000&page=0&size=20
X-Merchant-Id: 7c9e6679-7425-40de-944b-e07fc1f90ae7
```

**Response HTTP 200:**
```json
{
  "data": [
    { "transactionId": "txn_abc123", "status": "APPROVED", "amountInCents": 8990, "createdAt": "..." },
    { "transactionId": "txn_def456", "status": "CARD_DECLINED", "amountInCents": 4500, "createdAt": "..." }
  ],
  "meta": { "page": 0, "size": 20, "pageSize": 20, "total": 2, "timestamp": "..." }
}
```

---

## 9. Efeitos Colaterais

Nenhum — operações de leitura apenas.

---

## 10. Performance

| Operação | P50 | P99 |
|----------|-----|-----|
| GET por ID | 15ms | 80ms |
| Listagem paginada | 30ms | 150ms |

---

## 11. Segurança

- `merchantId` extraído do header `X-Merchant-Id` (injetado pelo gateway) — nunca do body
- Isolamento de dados garantido em nível de query (`AND merchant_id = ?`)
- Acesso negado retorna 403 — não revela existência de transações de outros merchants
- `cardToken` nunca retornado — apenas `paymentMethodId`
