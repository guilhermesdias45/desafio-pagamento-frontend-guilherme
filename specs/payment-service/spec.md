# Spec: Payment Service

**ID:** SPEC-PAY-001
**Serviço:** payment-service
**Status:** Draft
**Revisores:** [ ] PM [ ] Arquiteto [ ] QA [ ] Security

---

## 1. Visão Geral

O payment-service é o núcleo financeiro do sistema. Processa transações de pagamento via Mercado Pago, gerencia estornos, e expõe consulta de histórico. Toda transação passa por score de fraude antes de atingir o gateway. Idempotência é garantida via Redis.

---

## 2. Endpoints

```
POST   /api/v1/transactions                           → processar pagamento
POST   /api/v1/transactions/{transactionId}/refund    → estornar transação
GET    /api/v1/transactions/{transactionId}           → consultar por ID
GET    /api/v1/transactions                           → listar (paginado)
```

### Headers obrigatórios (todos os endpoints)

| Header | Tipo | Descrição |
|--------|------|-----------|
| X-Merchant-Id | UUID (v4) | ID do merchant autenticado. Usado para autorização e isolamento de dados. |

---

## 3. OPERAÇÃO: Processar Transação

### 3.1 Assinatura

**Service:** `TransactionService.processTransaction(TransactionRequest request): TransactionResult`

### 3.2 Input — TransactionRequest

| Campo | Tipo | Obrigatório | Regra |
|-------|------|-------------|-------|
| amountInCents | Long | Sim | Intervalo [1, 999999] — R$0,01 a R$9.999,99 |
| currency | String | Sim | "BRL" (único suportado no MVP) |
| customerId | UUID | Sim | UUID válido, cliente existente no user-service |
| orderId | UUID | Sim | UUID válido, pedido existente no order-service com status PENDING |
| cardToken | String | Sim | Token gerado pelo Mercado Pago JS SDK (32 chars hex) |
| paymentMethodId | String | Sim | "visa", "master", "elo", "pix" |
| installments | Integer | Não | Default 1; max 12; só para cartão de crédito |
| idempotencyKey | UUID | Sim | UUID único; não pode ter sido usado nas últimas 24h |

### 3.3 Output — TransactionResult (sealed)

**Sucesso:**

| Campo | Tipo | Descrição |
|-------|------|-----------|
| transactionId | String | Formato: txn_XXXXX (interno) |
| mpPaymentId | Long | ID do pagamento no Mercado Pago |
| orderId | UUID | ID do pedido associado |
| status | String | "APPROVED" |
| processingTimeMs | Long | Tempo total de processamento |

**Falha:**

| Campo | Tipo | Descrição |
|-------|------|-----------|
| status | String | "FAILURE" |
| errorCode | String | Ver tabela abaixo |
| message | String | Mensagem amigável para o usuário |
| retryable | Boolean | Se o cliente pode tentar novamente |
| processingTimeMs | Long | Tempo até a falha |

### 3.4 Códigos de Erro

| Código | HTTP | Retryable | Descrição |
|--------|------|-----------|-----------|
| INVALID_AMOUNT | 400 | false | Valor fora do intervalo permitido |
| INVALID_CURRENCY | 400 | false | Moeda não suportada |
| INVALID_CARD_TOKEN | 400 | false | Token de cartão inválido ou expirado |
| DUPLICATE_IDEMPOTENCY_KEY | 409 | false | Chave já processada nas últimas 24h |
| ORDER_NOT_FOUND | 404 | false | Pedido não encontrado |
| ORDER_NOT_PENDING | 422 | false | Pedido não está em status PENDING |
| CARD_DECLINED | 422 | true | Gateway recusou o cartão |
| INSUFFICIENT_FUNDS | 422 | true | Saldo insuficiente |
| SUSPECTED_FRAUD | 422 | false | Score de fraude acima do threshold (90) |
| RATE_LIMIT_EXCEEDED | 429 | true | Mais de 100 transações/min pelo mesmo cliente |
| MP_GATEWAY_TIMEOUT | 503 | true | Mercado Pago não respondeu em 800ms |
| INTERNAL_ERROR | 500 | true | Erro interno inesperado |

### 3.5 Pré-condições

- `amountInCents` entre 1 e 999999
- `currency` é "BRL"
- `customerId` existe e está ativo (validado via chamada ao user-service ou JWT claims)
- `orderId` existe no order-service com status PENDING
- `cardToken` tem 32 caracteres hexadecimais (token MP válido)
- `idempotencyKey` não foi processado nas últimas 24h (Redis)
- Cliente não está com rate limit ativo (máx 100 transações/min por customerId)

### 3.6 Pós-condições — Sucesso

- Transação gravada no PostgreSQL com `status = APPROVED`
- `idempotencyKey` registrado com TTL de 24h no Redis
- Evento `transaction.completed` publicado no Kafka
- Email de confirmação enfileirado (notification-service consome)
- Order-service atualiza pedido para PAID (consome evento Kafka)
- Métricas New Relic atualizadas
- `processingTimeMs ≤ 1000` (P99 SLA)

### 3.7 Pós-condições — Falha

- Nenhuma transação gravada (exceto `CARD_DECLINED` e `SUSPECTED_FRAUD`, que gravam com status negativo para auditoria)
- `idempotencyKey` NÃO registrado (permite retry com nova tentativa)
- Evento `transaction.failed` publicado no Kafka
- Logs de auditoria gravados sem dados sensíveis

### 3.8 Invariantes

1. Se `status = APPROVED`, então `transactionId` e `mpPaymentId` são sempre definidos
2. Se `status = FAILURE`, então `errorCode` e `message` são sempre definidos
3. `amountInCents` nunca muda durante o processamento
4. A mesma `idempotencyKey` retorna sempre o mesmo resultado (idempotência)
5. `cardToken` nunca aparece em logs — apenas `paymentMethodId` e últimos 4 dígitos
6. Fraud score nunca é compartilhado com o cliente

### 3.9 Fluxo Interno

```
Validação (50ms)
    ↓
Redis idempotency check (5ms)
    ↓
fraud-service.score() — chamada REST interna (200ms P99)
    ↓ [score < 90]
Mercado Pago API — POST /v1/payments (500ms P99)
    ↓
PostgreSQL write (80ms)
    ↓
Kafka publish (20ms)
    ↓
Response HTTP 201
```

### 3.10 Autenticação e Autorização do Merchant

**Padrão de autenticação:** `merchantId` (UUID v4) é extraído do header `X-Merchant-Id` em todos os endpoints. O gateway de API valida o JWT e propaga o merchantId autenticado.

**Isolamento de dados:** Todas as queries filtram por `merchantId`:
- `TransactionRepository.findByCustomerIdAndMerchantId()`
- `TransactionRepository.findByMerchantId()`
- `Transaction.findById()` verifica ownership antes de retornar

**Modelo de autorização:**
- Merchant só pode visualizar/editar transações do próprio merchant
- `findById()` retorna vazio se `transaction.merchantId != request.merchantId` → HTTP 403
- `refund()` lança `INSUFFICIENT_PERMISSIONS` se merchant não é dono da transação
- Listagem filtra automaticamente por merchantId (não expõe transações de outros merchants)

### 3.11 Casos Extremos

#### CE-001: Transação duplicada (mesma idempotencyKey)
- **Input:** Segunda requisição com `idempotencyKey` idêntico dentro de 24h
- **Comportamento:** Cache hit no Redis → retornar resultado original sem processar
- **Output:** Mesmo `transactionId` e `mpPaymentId` da requisição original, HTTP 200

#### CE-002: Valor zero ou negativo
- **Input:** `amountInCents = 0` ou `amountInCents = -100`
- **Comportamento:** Rejeitar na validação, antes de qualquer I/O
- **Output:** `errorCode = INVALID_AMOUNT`, HTTP 400

#### CE-003: Cartão recusado pelo gateway
- **Input:** `cardToken` válido mas cartão sem fundos ou bloqueado
- **Comportamento:** Gravar tentativa para auditoria, retornar falha
- **Output:** `errorCode = CARD_DECLINED`, `retryable = true`, HTTP 422

#### CE-004: Fraude detectada
- **Input:** Score do fraud-service > 90
- **Comportamento:** Bloquear antes de atingir Mercado Pago, gravar para análise
- **Output:** `errorCode = SUSPECTED_FRAUD`, `retryable = false`, HTTP 422

#### CE-005: Mercado Pago timeout
- **Input:** MP não responde em 800ms
- **Comportamento:** Cancelar chamada, não gravar transação, evento de falha no Kafka
- **Output:** `errorCode = MP_GATEWAY_TIMEOUT`, `retryable = true`, HTTP 503

#### CE-006: Pedido já pago
- **Input:** `orderId` com status PAID (tentativa de pagar duas vezes)
- **Comportamento:** Rejeitar sem chamar MP ou fraud-service
- **Output:** `errorCode = ORDER_NOT_PENDING`, HTTP 422

#### CE-007: Rate limit excedido
- **Input:** Cliente enviou mais de 100 transações no último minuto
- **Comportamento:** Rejeitar sem processar
- **Output:** `errorCode = RATE_LIMIT_EXCEEDED`, `retryable = true`, header `Retry-After`, HTTP 429

### 3.12 Exemplos Concretos

#### Exemplo 1 — Sucesso

**Request:**
```http
POST /api/v1/transactions
X-Merchant-Id: 7c9e6679-7425-40de-944b-e07fc1f90ae7
Content-Type: application/json

{
  "amountInCents": 8990,
  "currency": "BRL",
  "customerId": "550e8400-e29b-41d4-a716-446655440000",
  "orderId": "660f9511-f30c-52e5-b827-557766551111",
  "cardToken": "ae4e50b2a8f3d5e9k8h1j7l4m6n2p0q1",
  "paymentMethodId": "visa",
  "installments": 1,
  "idempotencyKey": "a1b2c3d4-e5f6-7890-abcd-ef1234567890"
}
```

**Response HTTP 201:**
```json
{
  "data": {
    "transactionId": "txn_xyz789",
    "mpPaymentId": 1234567890,
    "orderId": "660f9511-f30c-52e5-b827-557766551111",
    "status": "APPROVED",
    "processingTimeMs": 547
  },
  "meta": {
    "timestamp": "2026-05-27T14:00:00Z",
    "requestId": "req_abc123"
  },
  "errors": []
}
```

**Efeitos colaterais:**
- `transactions` → registro com `status = APPROVED`
- Redis → `idempotency:a1b2c3d4...` com TTL 24h
- Kafka → `transaction.completed` event
- notification-service → email de confirmação
- order-service → pedido atualizado para PAID

#### Exemplo 2 — Falha (cartão recusado)

**Response HTTP 422:**
```json
{
  "data": {
    "status": "FAILURE",
    "processingTimeMs": 245
  },
  "meta": { "timestamp": "2026-05-27T14:00:00Z", "requestId": "req_def456" },
  "errors": [{
    "code": "CARD_DECLINED",
    "message": "Seu cartão foi recusado. Verifique o limite ou tente outro cartão.",
    "retryable": true
  }]
}
```

---

## 4. OPERAÇÃO: Estornar Transação

### 4.1 Assinatura

**Service:** `TransactionService.refundTransaction(String transactionId, RefundRequest request): RefundResult`

### 4.2 Input — RefundRequest

| Campo | Tipo | Obrigatório | Regra |
|-------|------|-------------|-------|
| amountInCents | Long | Não | Se ausente, estorno total. Se presente, intervalo [1, valorOriginal] |
| reason | String | Sim | "CUSTOMER_REQUEST", "DUPLICATE", "FRAUD", "PRODUCT_NOT_DELIVERED" |
| requestedBy | UUID | Sim | ID do usuário que solicitou (merchant dono ou admin) |
| idempotencyKey | UUID | Sim | Previne estorno duplo |

### 4.3 Códigos de Erro

| Código | HTTP | Retryable | Descrição |
|--------|------|-----------|-----------|
| TRANSACTION_NOT_FOUND | 404 | false | Transação não existe |
| TRANSACTION_NOT_REFUNDABLE | 422 | false | Status da transação não permite estorno |
| AMOUNT_EXCEEDS_ORIGINAL | 422 | false | Valor do estorno maior que o valor não estornado |
| ALREADY_FULLY_REFUNDED | 422 | false | Transação já foi totalmente estornada |
| REFUND_WINDOW_EXPIRED | 422 | false | Prazo de 90 dias para estorno expirou |
| INSUFFICIENT_PERMISSIONS | 403 | false | Usuário não tem permissão para estornar |
| MP_GATEWAY_ERROR | 503 | true | Mercado Pago retornou erro no estorno |

### 4.4 Casos Extremos

#### CE-001: Estorno duplo (mesma idempotencyKey)
- Retornar resultado original sem criar novo estorno

#### CE-002: Estorno parcial múltiplas vezes
- Permitir, desde que a soma dos estornos não exceda o valor original

#### CE-003: Estorno após 90 dias
- Rejeitar com `REFUND_WINDOW_EXPIRED`

#### CE-004: Merchant tentando estornar transação de outro merchant
- Retornar `INSUFFICIENT_PERMISSIONS`, HTTP 403

### 4.5 Pós-condições — Sucesso

- Estorno registrado no PostgreSQL com `status = COMPLETED`
- Transação original: `refundedAmountInCents` incrementado
- Se estorno total: `status` da transação → `FULLY_REFUNDED`
- Se estorno parcial: `status` da transação → `PARTIALLY_REFUNDED`
- Evento `transaction.refunded` publicado no Kafka
- notification-service → email de confirmação de estorno ao cliente
- order-service → pedido atualizado para REFUNDED (se estorno total)

---

## 5. OPERAÇÃO: Consultar Transação

### 5.1 Endpoints

```
GET /api/v1/transactions/{transactionId}
GET /api/v1/transactions?customerId={uuid}&page={n}&size={n}&sort=createdAt,desc
```

### 5.2 Output — TransactionDetail

| Campo | Tipo | Descrição |
|-------|------|-----------|
| transactionId | String | ID interno |
| mpPaymentId | Long | ID no Mercado Pago |
| status | String | APPROVED, DECLINED, SUSPECTED_FRAUD, FULLY_REFUNDED, PARTIALLY_REFUNDED |
| amountInCents | Long | Valor em centavos |
| currency | String | BRL |
| cardBrand | String | visa, master, elo (nunca número completo) |
| cardLastFour | String | Últimos 4 dígitos |
| orderId | UUID | Pedido associado |
| createdAt | Instant | Data/hora UTC |
| updatedAt | Instant | Última atualização |
| refunds | List\<RefundSummary\> | Estornos associados |
| processingTimeMs | Long | Tempo de processamento original |

**Campos NUNCA retornados:** número completo do cartão, CVV, cardToken.

### 5.3 Casos Extremos

#### CE-001: Transação não encontrada
- HTTP 404

#### CE-002: Acesso negado (usuário tentando ver transação de outro)
- HTTP 403 — nunca HTTP 404 (não revelar existência)

#### CE-003: Paginação além do limite
- Máx 100 itens por página; se `size > 100`, usar 100

### 5.4 Performance

- Leitura simples: P99 < 100ms (cache Redis por 60s, key: `transaction:{id}`)
- Listagem paginada: P99 < 300ms

---

## 6. Efeitos Colaterais Globais

| Efeito | Síncrono/Assíncrono | Obrigatório |
|--------|---------------------|-------------|
| Gravar transação no PostgreSQL | Síncrono | Sim |
| Registrar idempotencyKey no Redis | Síncrono | Sim |
| Publicar evento no Kafka | Assíncrono | Sim |
| Enviar email de confirmação | Assíncrono via Kafka | Não (falha silenciosa) |
| Gravar log de auditoria | Síncrono | Sim |
| Atualizar métricas New Relic | Síncrono | Sim |

---

## 7. Performance

| Etapa | P50 | P99 |
|-------|-----|-----|
| Validação | 10ms | 30ms |
| Idempotency check (Redis) | 5ms | 15ms |
| Fraud Detection (REST interno) | 100ms | 200ms |
| Mercado Pago gateway | 150ms | 500ms |
| PostgreSQL write | 20ms | 80ms |
| Kafka publish | 5ms | 20ms |
| **Total (processamento)** | **290ms** | **845ms** |

SLA: **P99 < 1.000ms**

---

## 8. Segurança

- `cardToken` nunca logado — apenas `paymentMethodId` e `cardLastFour`
- `customerId` validado via JWT claims antes de qualquer operação
- `merchantId` extraído do header `X-Merchant-Id`; JWT deve conter merchantId válido
- Isolamento de dados: todas as queries filtram por `merchantId` (nunca expõe dados de outros merchants)
- Rate limiting por `customerId` via Redis (100 req/min)
- Fraud score calculado antes de atingir o gateway
- Toda transação tem log de auditoria imutável
- Comunicação com Mercado Pago somente via HTTPS/TLS 1.3
- `idempotencyKey` previne double-charge em falhas de rede
- Webhook do Mercado Pago validado via `x-signature` antes de processar

---

## 9. Contas de Teste MercadoPago

### 9.1 Visão Geral

O payment-service armazena credenciais de duas contas de teste do MercadoPago (seller e buyer)
em uma tabela dedicada `mp_test_accounts`. Essas contas são registradas exclusivamente via script
SQL manual executado pelo operador do sistema — não há endpoint para criá-las via API.

### 9.2 Tabela: `mp_test_accounts`

| Campo | Tipo | Descrição |
|-------|------|-----------|
| id | UUID (PK) | Gerado automaticamente |
| type | VARCHAR(16) | `SELLER` ou `BUYER` |
| mp_user_id | BIGINT | ID numérico da conta no MercadoPago |
| email | VARCHAR(255) | Usuário/nickname da conta de teste |
| password_enc | TEXT | Senha criptografada |
| verification_code | VARCHAR(16) | Código de verificação 2FA |
| access_token_enc | TEXT | Access token criptografado (nullable — Fase 1) |
| refresh_token_enc | TEXT | Refresh token criptografado (nullable) |
| public_key | VARCHAR(255) | Chave pública (nullable) |
| token_expires_at | TIMESTAMPTZ | Expiração do access_token (nullable) |
| created_at | TIMESTAMPTZ | Data de criação |
| updated_at | TIMESTAMPTZ | Última atualização |

**Restrições:**
- `UNIQUE(email)` — email é único
- `UNIQUE INDEX` parcial em `type WHERE type = 'SELLER'` — apenas uma conta seller

### 9.3 Fluxo de Pagamento com Contas de Teste

```
┌──────────────┐     ┌──────────────────┐     ┌───────────────────┐
│  Comprador   │────▶│  Payment Service  │────▶│  MercadoPago API  │
│ (test@test…) │     │  (token da Fase 1)│     │  (sandbox)        │
└──────────────┘     └──────────────────┘     └───────────────────┘
                             │
                             ▼
                    ┌──────────────────┐
                    │  mp_test_accounts│
                    │  (seller creds)  │
                    └──────────────────┘
```

- **Fase 1 (atual):** Usa `MERCADOPAGO_ACCESS_TOKEN` global como seller token.
  `access_token_enc` fica NULL. Pagamentos usam `test@testuser.com` como payer.email.
- **Fase 2 (futura):** Obtém seller access token via OAuth authorization_code,
  armazena criptografado na tabela, e usa `RequestOptions` nas chamadas ao gateway.

### 9.4 Casos Extremos

#### CE-008: Credenciais MP ausentes ou token expirado

| Condição | Comportamento | Output |
|----------|--------------|--------|
| `access_token_enc = NULL` | Usa token global (`MERCADOPAGO_ACCESS_TOKEN`) como fallback | Transação processa normalmente |
| Token expirado e sem refresh | Usa token global como fallback, log warn | Transação processa, alerta em log |
| Nenhuma conta seller no banco | Log warn, usa token global | Transação processa com token global |

#### CE-009: Múltiplas contas buyer

O schema atual usa `UNIQUE(email)`. Se no futuro múltiplos buyers forem necessários,
a unique constraint deve ser removida e a query ajustada para aceitar o mais recente.

### 9.5 Inserção Manual

A única forma de registrar contas é via script SQL:
```sql
INSERT INTO mp_test_accounts (type, mp_user_id, email, password_enc, verification_code)
VALUES ('SELLER', ..., ..., ..., ...),
       ('BUYER',  ..., ..., ..., ...);
```

### 9.6 Segurança

- `password_enc` e `access_token_enc` são criptografados em repouso (PGP ou AES-256)
- Credenciais nunca são expostas em logs, responses de API, ou métricas
- Apenas o payment-service acessa a tabela `mp_test_accounts`
