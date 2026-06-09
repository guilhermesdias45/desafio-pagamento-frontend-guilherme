# Especificação: Estornar Transação

**ID:** SPEC-PAY-002  
**Serviço:** payment-service  
**Status:** Aprovado  
**Revisores:** [x] PM [x] Arquiteto [x] QA [x] Security

---

## 1. Assinatura

```java
POST /api/v1/transactions/{transactionId}/refund
Headers:
  X-Merchant-Id: {uuid}
Body: RefundRequest

// Service
public RefundResponse refund(
    String transactionId,
    RefundRequest request,
    UUID merchantId
)
```

---

## 2. Tipos de Dados

### Input — RefundRequest

```java
public record RefundRequest(
    Long amountInCents,       // opcional; se null → estorno total
    @NotBlank String reason,  // CUSTOMER_REQUEST | DUPLICATE | FRAUD | PRODUCT_NOT_DELIVERED
    @NotNull UUID requestedBy,
    @NotNull UUID idempotencyKey
) {}
```

### Output — RefundResponse (HTTP 200)

```java
public record RefundResponse(
    String refundId,
    String transactionId,
    Long amountRefundedInCents,
    boolean fullRefund,
    String status,             // "COMPLETED"
    long processingTimeMs
) {}
```

---

## 3. Pré-condições

- `transactionId` existe no banco
- `merchantId` (do header) é o dono da transação
- Transação com `status = APPROVED` ou `status = PARTIALLY_REFUNDED`
- Criada há menos de 90 dias
- `amountInCents` (se presente): entre 1 e valor ainda não estornado
- `idempotencyKey` não usado em outros estornos

---

## 4. Pós-condições (Sucesso)

- Estorno registrado no PostgreSQL com `status = COMPLETED`
- `refundedAmountInCents` da transação original incrementado
- Se estorno total: `status` da transação → `FULLY_REFUNDED`
- Se estorno parcial: `status` da transação → `PARTIALLY_REFUNDED`
- Evento `transaction.refunded` publicado no Kafka
- notification-service envia email de confirmação do estorno ao cliente

---

## 5. Pós-condições (Erro)

| Código | HTTP | Retryable | Descrição |
|--------|------|-----------|-----------|
| TRANSACTION_NOT_FOUND | 404 | false | Transação não existe |
| INSUFFICIENT_PERMISSIONS | 403 | false | Merchant não é dono da transação |
| TRANSACTION_NOT_REFUNDABLE | 422 | false | Status não permite estorno |
| AMOUNT_EXCEEDS_ORIGINAL | 422 | false | Valor do estorno maior que saldo disponível |
| ALREADY_FULLY_REFUNDED | 422 | false | Transação já totalmente estornada |
| REFUND_WINDOW_EXPIRED | 422 | false | Prazo de 90 dias expirou |
| MP_GATEWAY_ERROR | 503 | true | Mercado Pago retornou erro no estorno |

---

## 6. Invariantes

1. Soma de todos os estornos nunca excede o valor original da transação
2. Estorno duplo com mesma `idempotencyKey` retorna o resultado original (idempotente)
3. Merchant só pode estornar transações do próprio `merchantId`
4. Transação `FULLY_REFUNDED` não aceita novos estornos

---

## 7. Casos Extremos

| ID | Input | Comportamento | Output |
|----|-------|--------------|--------|
| CE-001 | idempotencyKey já usado (retry) | Retornar estorno original | 200 + mesmo refundId |
| CE-002 | Dois estornos parciais somando o total | Permitir ambos; segundo completa estorno total | 200 (FULLY_REFUNDED) |
| CE-003 | Estorno após 90 dias | Rejeitar | 422 REFUND_WINDOW_EXPIRED |
| CE-004 | Merchant errado tentando estornar | 403 (nunca 404) | 403 INSUFFICIENT_PERMISSIONS |
| CE-005 | amountInCents maior que saldo restante | Rejeitar | 422 AMOUNT_EXCEEDS_ORIGINAL |

---

## 8. Exemplos Concretos

### Exemplo 1 — Estorno total

**Request:**
```json
{
  "reason": "CUSTOMER_REQUEST",
  "requestedBy": "merchant-user-uuid",
  "idempotencyKey": "ref-idem-uuid-001"
}
```

**Response HTTP 200:**
```json
{
  "data": {
    "refundId": "ref_xyz123",
    "transactionId": "txn_abc123",
    "amountRefundedInCents": 8990,
    "fullRefund": true,
    "status": "COMPLETED",
    "processingTimeMs": 312
  }
}
```

### Exemplo 2 — Merchant sem permissão

**Response HTTP 403:**
```json
{
  "errors": [{
    "code": "INSUFFICIENT_PERMISSIONS",
    "message": "INSUFFICIENT_PERMISSIONS",
    "retryable": false
  }]
}
```

---

## 9. Efeitos Colaterais

| Efeito | Síncrono | Obrigatório |
|--------|----------|-------------|
| Gravar estorno no PostgreSQL | Sim | Sim |
| Atualizar status da transação original | Sim | Sim |
| Publicar `transaction.refunded` no Kafka | Não | Best-effort |
| Email de confirmação de estorno | Não (via Kafka) | Best-effort |
| order-service atualiza pedido para REFUNDED | Não (via Kafka) | Best-effort |

---

## 10. Performance

| Etapa | P50 | P99 |
|-------|-----|-----|
| Validação + ownership check | 15ms | 40ms |
| Mercado Pago estorno | 100ms | 400ms |
| PostgreSQL write | 20ms | 60ms |
| **Total** | **135ms** | **500ms** |

---

## 11. Segurança

- `merchantId` extraído do header `X-Merchant-Id` (injetado pelo gateway) — não confiar em body
- Acesso negado retorna 403 — nunca 404 para não revelar existência da transação
- `requestedBy` gravado no log de auditoria para rastreabilidade
- Janela de 90 dias limita exposição a chargebacks tardios
