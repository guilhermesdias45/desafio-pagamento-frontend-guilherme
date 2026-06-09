# Especificação: Criar Pedido

**ID:** SPEC-ORD-001  
**Serviço:** order-service  
**Status:** Aprovado  
**Revisores:** [x] PM [x] Arquiteto [x] QA [x] Security

---

## 1. Assinatura

```java
POST /api/v1/orders
Headers:
  X-User-Id: {uuid}         ← injetado pelo api-gateway a partir do JWT
  X-Merchant-Id: {uuid}     ← injetado pelo api-gateway (apenas para MERCHANT_OWNER)
  X-User-Role: {role}       ← injetado pelo api-gateway
Body: CreateOrderRequest

// Service
public OrderResult createOrder(CreateOrderRequest request, UUID customerId)
```

---

## 2. Tipos de Dados

### Input — CreateOrderRequest

```java
public record CreateOrderRequest(
    @NotNull UUID merchantId,
    @NotEmpty List<@Valid OrderItemRequest> items,
    @NotNull UUID idempotencyKey
) {}

public record OrderItemRequest(
    @NotBlank String productId,
    @NotBlank @Size(max = 255) String description,
    @Min(1) @Max(999) Integer quantity,
    @Min(1) @Max(999999) Long unitPriceInCents
) {}
```

### Output — OrderResult (sealed interface)

```java
public sealed interface OrderResult
    permits OrderResult.Created, OrderResult.Duplicate, OrderResult.Failed {

    record Created(
        UUID orderId,
        String status,          // "PENDING"
        Long totalInCents,
        List<OrderItemResponse> items,
        Instant expiresAt,
        Instant createdAt
    ) implements OrderResult {}

    record Duplicate(
        UUID orderId,
        String status,
        Long totalInCents,
        List<OrderItemResponse> items,
        Instant expiresAt,
        Instant createdAt
    ) implements OrderResult {}    // HTTP 200 em vez de 201

    record Failed(
        String errorCode,
        String message,
        boolean retryable
    ) implements OrderResult {}
}
```

### Output — OrderItemResponse

```java
public record OrderItemResponse(
    String productId,
    String description,
    Integer quantity,
    Long unitPriceInCents,
    Long subtotalInCents    // quantity × unitPriceInCents
) {}
```

---

## 3. Pré-condições

- `customerId` extraído do header `X-User-Id` (injetado pelo gateway a partir do JWT)
- `merchantId` existe e está ativo no sistema
- Lista de `items` contém pelo menos 1 item
- `quantity` de cada item: entre 1 e 999
- `unitPriceInCents` de cada item: entre 1 e 999999
- Total calculado (soma de `quantity × unitPriceInCents`) ≤ 999999 centavos (R$9.999,99)
- `idempotencyKey` não utilizado nas últimas 24h

---

## 4. Pós-condições (Sucesso)

- Pedido gravado no PostgreSQL com `status = PENDING`
- `totalInCents` calculado pelo servidor (soma dos subtotais dos itens)
- `idempotencyKey` registrado no Redis com TTL 24h: `order_idempotency:{key}` → `orderId`
- Job agendado para cancelar o pedido automaticamente após 15 minutos sem pagamento
- Evento `order.created` publicado no Kafka
- `expiresAt` = `createdAt` + 15 minutos

---

## 5. Pós-condições (Erro)

| Código              | HTTP | Retryable | Descrição                                   |
|---------------------|------|-----------|---------------------------------------------|
| DUPLICATE_ORDER     | 409  | false     | Mesmo `idempotencyKey` nas últimas 24h      |
| EMPTY_ORDER         | 400  | false     | Lista de items vazia                        |
| INVALID_ITEM_PRICE  | 400  | false     | `unitPriceInCents` ≤ 0 ou > 999999         |
| INVALID_QUANTITY    | 400  | false     | `quantity` ≤ 0 ou > 999                     |
| MERCHANT_NOT_FOUND  | 404  | false     | Merchant não existe ou está inativo         |
| TOTAL_EXCEEDS_LIMIT | 400  | false     | Total calculado > 999999 centavos           |

---

## 6. Invariantes

1. `totalInCents` é sempre calculado pelo servidor — nunca aceitar total pré-calculado do cliente
2. `customerId` é sempre extraído do JWT — nunca aceitar do body da requisição
3. Um pedido PENDING pode receber apenas um pagamento
4. Items de um pedido são imutáveis após criação
5. Pedido PAID nunca volta para PENDING
6. Pedido com `idempotencyKey` duplicada retorna o pedido original sem criar novo registro

---

## 7. Casos Extremos

| ID     | Input                                       | Comportamento                                         | Output                           |
|--------|---------------------------------------------|-------------------------------------------------------|----------------------------------|
| CE-001 | `idempotencyKey` já usado (retry < 24h)     | Retornar pedido original sem criar novo              | HTTP 200 + mesmo orderId         |
| CE-002 | Pedido expira sem pagamento após 15 min     | Job cancela automaticamente; status → CANCELLED       | Evento `order.cancelled` no Kafka|
| CE-003 | Merchant inativo ou suspenso                | Rejeitar; não revelar que merchant existe mas suspenso| HTTP 404 MERCHANT_NOT_FOUND      |
| CE-004 | Item com `unitPriceInCents = 0`             | Rejeitar na validação antes de qualquer I/O           | HTTP 400 INVALID_ITEM_PRICE      |
| CE-005 | 1 item, `quantity=999`, `unitPrice=999999`  | Total = 998.001.001 > 999999 → rejeitar               | HTTP 400 TOTAL_EXCEEDS_LIMIT     |
| CE-006 | Lista de items com 0 elementos              | Rejeitar na validação antes de qualquer I/O           | HTTP 400 EMPTY_ORDER             |

---

## 8. Exemplos Concretos

### Exemplo 1 — Criação com sucesso (2 itens)

**Request:**
```http
POST /api/v1/orders
X-User-Id: 550e8400-e29b-41d4-a716-446655440000
X-User-Role: CUSTOMER

{
  "merchantId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
  "items": [
    {
      "productId": "prod_vestido_azul",
      "description": "Vestido Azul Floral Tam M",
      "quantity": 1,
      "unitPriceInCents": 8990
    },
    {
      "productId": "prod_cinto_preto",
      "description": "Cinto Preto Couro",
      "quantity": 2,
      "unitPriceInCents": 3990
    }
  ],
  "idempotencyKey": "a1b2c3d4-e5f6-7890-abcd-ef1234567890"
}
```

**Response HTTP 201:**
```json
{
  "data": {
    "orderId": "660f9511-f30c-52e5-b827-557766551111",
    "status": "PENDING",
    "totalInCents": 16970,
    "items": [
      {
        "productId": "prod_vestido_azul",
        "description": "Vestido Azul Floral Tam M",
        "quantity": 1,
        "unitPriceInCents": 8990,
        "subtotalInCents": 8990
      },
      {
        "productId": "prod_cinto_preto",
        "description": "Cinto Preto Couro",
        "quantity": 2,
        "unitPriceInCents": 3990,
        "subtotalInCents": 7980
      }
    ],
    "expiresAt": "2026-06-09T14:15:00Z",
    "createdAt": "2026-06-09T14:00:00Z"
  },
  "meta": { "timestamp": "2026-06-09T14:00:00Z" },
  "errors": []
}
```

### Exemplo 2 — Idempotência (pedido duplicado)

**Request:** Mesmo `idempotencyKey` enviado novamente dentro de 24h.

**Response HTTP 200** (não 201 — pedido já existia):
```json
{
  "data": {
    "orderId": "660f9511-f30c-52e5-b827-557766551111",
    "status": "PENDING",
    "totalInCents": 16970,
    "items": [...],
    "expiresAt": "2026-06-09T14:15:00Z",
    "createdAt": "2026-06-09T14:00:00Z"
  },
  "meta": { "timestamp": "2026-06-09T14:00:30Z" },
  "errors": []
}
```

### Exemplo 3 — Total excede limite

**Response HTTP 400:**
```json
{
  "errors": [{
    "code": "TOTAL_EXCEEDS_LIMIT",
    "message": "O valor total do pedido não pode exceder R$9.999,99.",
    "retryable": false
  }]
}
```

---

## 9. Efeitos Colaterais

| Efeito                                        | Quando    | Síncrono | Obrigatório   |
|-----------------------------------------------|-----------|----------|---------------|
| Gravar pedido no PostgreSQL                   | Sucesso   | Sim      | Sim           |
| Registrar `idempotencyKey` no Redis (TTL 24h) | Sucesso   | Sim      | Sim           |
| Agendar cancelamento automático (15 min)      | Sucesso   | Sim      | Sim           |
| Publicar `order.created` no Kafka             | Sucesso   | Não      | Best-effort   |

---

## 10. Performance

| Operação                         | P50   | P99    |
|----------------------------------|-------|--------|
| Validação + cálculo do total     | 5ms   | 15ms   |
| Verificação de idempotência (Redis) | 3ms | 10ms  |
| PostgreSQL write                 | 20ms  | 60ms   |
| Kafka publish                    | 5ms   | 20ms   |
| **Total**                        | **33ms** | **105ms** |

---

## 11. Segurança

- `customerId` extraído do header `X-User-Id` (injetado pelo gateway) — nunca confiar no body
- `totalInCents` sempre calculado no servidor — previne manipulação de preço pelo cliente
- `merchantId` do body é validado contra o banco — previne criação de pedidos para merchants inexistentes
- Acesso negado retorna HTTP 403 — nunca 404 para não revelar existência de recursos
- Rate limiting: 50 pedidos/min por `customerId` via Redis
- `idempotencyKey` previne criação duplicada em falhas de rede ou retries
- Dados de pedidos retidos conforme política LGPD (consentimento explícito do cliente)
