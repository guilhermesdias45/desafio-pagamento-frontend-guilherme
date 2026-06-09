# Especificação: Processar Transação

**ID:** SPEC-PAY-001  
**Serviço:** payment-service  
**Status:** Aprovado  
**Revisores:** [x] PM [x] Arquiteto [x] QA [x] Security

---

## 1. Assinatura

```java
POST /api/v1/transactions
Headers:
  X-Merchant-Id: {uuid}       ← injetado pelo api-gateway a partir do JWT
  X-User-Email: {email}       ← injetado pelo api-gateway
  X-Forwarded-For: {ip}       ← injetado pelo api-gateway
Body: TransactionRequest

// Service
public TransactionResult processTransaction(
    TransactionRequest request,
    String customerEmail,
    UUID merchantId,
    String ipAddress
)
```

---

## 2. Tipos de Dados

### Input — TransactionRequest

```java
public record TransactionRequest(
    @NotNull @Min(1) @Max(999999) Long amountInCents,
    @NotBlank String currency,           // "BRL"
    @NotNull UUID customerId,
    @NotNull UUID orderId,
    @NotBlank @Pattern(regexp = "^[a-fA-F0-9]{32}$") String cardToken,
    @NotBlank String paymentMethodId,    // "visa", "master", "elo", "pix"
    @Min(1) @Max(12) Integer installments,  // default 1
    @NotNull UUID idempotencyKey
) {}
```

### Output — TransactionResult (sealed interface)

```java
public sealed interface TransactionResult
    permits TransactionResult.Approved, TransactionResult.Failed {

    record Approved(
        String transactionId,    // formato: txn_XXXXX
        Long mpPaymentId,        // ID no Mercado Pago
        UUID orderId,
        long processingTimeMs,
        boolean duplicate        // true se idempotência; HTTP 200 em vez de 201
    ) implements TransactionResult {}

    record Failed(
        String errorCode,
        String message,
        boolean retryable,
        long processingTimeMs
    ) implements TransactionResult {}
}
```

---

## 3. Pré-condições

- `amountInCents` entre 1 e 999999
- `currency` = "BRL"
- `cardToken` = 32 chars hexadecimais (formato do Mercado Pago JS SDK)
- `idempotencyKey` não processado nas últimas 24h (Redis)
- `orderId` existe no order-service com `status = PENDING`
- `customerId` existe e está ativo no user-service
- Cliente não está em rate limit (< 100 transações/min por `customerId`)

---

## 4. Pós-condições (Sucesso)

- Transação gravada no PostgreSQL com `status = APPROVED`
- `idempotencyKey` registrado no Redis com TTL 24h: `idempotency:{key}` → `transactionId`
- Evento `transaction.completed` publicado no Kafka
- Order-service consome evento e atualiza pedido para `PAID`
- notification-service envia email de confirmação ao cliente e ao merchant
- Log de auditoria gravado com `PAYMENT_APPROVED` (sem dados sensíveis)

---

## 5. Pós-condições (Erro)

| Código | HTTP | Retryable | Descrição |
|--------|------|-----------|-----------|
| INVALID_AMOUNT | 400 | false | Valor fora do intervalo [1, 999999] |
| INVALID_CURRENCY | 400 | false | Moeda não suportada |
| INVALID_CARD_TOKEN | 400 | false | Token de cartão com formato inválido |
| DUPLICATE_IDEMPOTENCY_KEY | 409 | false | Chave já processada — retorna resultado original |
| ORDER_NOT_FOUND | 404 | false | Pedido não encontrado |
| CUSTOMER_NOT_FOUND | 404 | false | Cliente não encontrado no user-service |
| ORDER_NOT_PENDING | 422 | false | Pedido não está em status PENDING |
| CARD_DECLINED | 422 | true | Gateway recusou o cartão |
| SUSPECTED_FRAUD | 422 | false | Score de fraude ≥ 90 |
| RATE_LIMIT_EXCEEDED | 429 | true | Mais de 100 transações/min pelo cliente |
| MP_GATEWAY_TIMEOUT | 503 | true | Mercado Pago timeout (> 800ms) |
| ORDER_SERVICE_UNAVAILABLE | 503 | true | Circuit breaker aberto para order-service |

---

## 6. Invariantes

1. `amountInCents` nunca muda durante o processamento
2. A mesma `idempotencyKey` retorna sempre o mesmo resultado (idempotência garantida via Redis)
3. `cardToken` nunca aparece em logs — apenas `paymentMethodId`
4. Score de fraude nunca é enviado ao cliente — apenas a decisão (`SUSPECTED_FRAUD`)
5. Transação gravada no banco SOMENTE após aprovação do gateway
6. Em caso de timeout do gateway, `idempotencyKey` NÃO é registrado (permite retry)

---

## 7. Casos Extremos

| ID | Input | Comportamento | Output |
|----|-------|--------------|--------|
| CE-001 | idempotencyKey duplicado (retry em 24h) | Cache hit no Redis → retornar resultado original | HTTP 200 + mesmo transactionId |
| CE-002 | amountInCents = 0 | Rejeitar na validação, antes de qualquer I/O | 400 INVALID_AMOUNT |
| CE-003 | Cartão recusado pelo gateway | Gravar tentativa para auditoria | 422 CARD_DECLINED, retryable: true |
| CE-004 | Score fraude ≥ 90 (BLOCK) | Bloquear antes de atingir MP | 422 SUSPECTED_FRAUD, retryable: false |
| CE-005 | MP não responde em 800ms | Cancelar, não gravar, evento de falha no Kafka | 503 MP_GATEWAY_TIMEOUT, retryable: true |
| CE-006 | Pedido já PAID (duplo pagamento) | Rejeitar sem chamar MP ou fraud-service | 422 ORDER_NOT_PENDING |
| CE-007 | > 100 transações/min pelo cliente | Rejeitar, header `Retry-After: 60` | 429 RATE_LIMIT_EXCEEDED |
| CE-008 | Circuit breaker orderService OPEN | Retornar 503 sem tentar chamar order-service | 503 ORDER_SERVICE_UNAVAILABLE |

---

## 8. Exemplos Concretos

### Exemplo 1 — Sucesso

**Request:**
```http
POST /api/v1/transactions
X-Merchant-Id: 7c9e6679-7425-40de-944b-e07fc1f90ae7
X-User-Email: cliente@email.com
X-Forwarded-For: 192.168.1.1

{
  "amountInCents": 8990,
  "currency": "BRL",
  "customerId": "550e8400-e29b-41d4-a716-446655440000",
  "orderId": "660f9511-f30c-52e5-b827-557766551111",
  "cardToken": "a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6",
  "paymentMethodId": "visa",
  "installments": 1,
  "idempotencyKey": "a1b2c3d4-e5f6-7890-abcd-ef1234567890"
}
```

**Response HTTP 201:**
```json
{
  "data": {
    "transactionId": "txn_abc123",
    "mpPaymentId": 1234567890,
    "orderId": "660f9511-f30c-52e5-b827-557766551111",
    "status": "APPROVED",
    "processingTimeMs": 547
  },
  "meta": { "timestamp": "2026-06-09T14:00:00Z" },
  "errors": []
}
```

### Exemplo 2 — Cartão recusado

**Response HTTP 422:**
```json
{
  "data": { "status": "FAILURE", "processingTimeMs": 245 },
  "meta": { "timestamp": "2026-06-09T14:00:00Z" },
  "errors": [{
    "code": "CARD_DECLINED",
    "message": "Seu cartão foi recusado. Verifique o limite ou tente outro cartão.",
    "retryable": true
  }]
}
```

---

## 9. Efeitos Colaterais

| Efeito | Quando | Síncrono | Obrigatório |
|--------|--------|----------|-------------|
| Gravar transação no PostgreSQL | Aprovado | Sim | Sim |
| Gravar tentativa no PostgreSQL | CARD_DECLINED, SUSPECTED_FRAUD | Sim | Sim |
| Registrar idempotencyKey no Redis | Aprovado | Sim | Sim |
| Gravar log de auditoria (JSONB) | Sempre | Sim | Sim |
| Publicar `transaction.completed` no Kafka | Aprovado | Não | Best-effort |
| Publicar `transaction.failed` no Kafka | Falha | Não | Best-effort |

---

## 10. Performance

| Etapa | P50 | P99 |
|-------|-----|-----|
| Validação + idempotência | 15ms | 40ms |
| fraud-service (REST interno) | 100ms | 200ms |
| Mercado Pago gateway | 150ms | 500ms |
| PostgreSQL write | 20ms | 80ms |
| Kafka publish | 5ms | 20ms |
| **Total** | **290ms** | **840ms** |

**SLA:** P99 < 1.000ms

---

## 11. Segurança

- `cardToken` nunca logado — apenas `paymentMethodId`
- `customerId` validado via chamada ao user-service (circuit breaker configurado)
- `merchantId` extraído do header `X-Merchant-Id` (injetado pelo gateway — não confiar em body)
- Score de fraude calculado antes de qualquer chamada ao gateway
- Toda transação tem log de auditoria imutável em coluna JSONB
- `idempotencyKey` previne double-charge em falhas de rede/retry
- Comunicação com Mercado Pago somente via HTTPS/TLS 1.3
