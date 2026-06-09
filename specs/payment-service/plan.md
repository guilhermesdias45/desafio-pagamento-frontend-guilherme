# Plano Técnico: Payment Service

**Spec:** [spec.md](spec.md)
**Status:** Draft
**Responsável:** Dev 1
**Sprint:** 1–2

---

## Decisões Técnicas

| Decisão | Escolha | Alternativa | Motivo |
|---------|---------|-------------|--------|
| Gateway de pagamento | Mercado Pago SDK Java 2.1.x | Stripe, PagBank | SDK oficial mantido, suporte completo a PIX e cartão BR |
| Idempotência | Redis TTL 24h (UUID key) | DB unique constraint | Latência < 10ms; DB seria gargalo |
| Fraud check | REST síncrono para fraud-service | Kafka assíncrono | Bloqueio obrigatório antes do gateway — não pode ser assíncrono |
| Timeout do gateway MP | 800ms | 1000ms | SLA P99 < 1s — gateway tem que sobrar 200ms |
| Transações negadas | Gravar no banco | Não gravar | Auditoria PCI DSS — todas as tentativas devem ser rastreáveis |
| Virtual Threads | Sim para I/O (MP, DB, Redis) | Threads fixas | Java 21 — I/O intensivo beneficia muito |

---

## Dependências

### Serviços consumidos
- `fraud-service` → `POST /internal/fraud/score` (síncrono, antes do gateway)
- `user-service` → JWT claims já validados pelo api-gateway (não precisa de chamada direta)
- Mercado Pago API → `POST /v1/payments`, `POST /v1/payments/{id}/refunds`

### Tópicos Kafka produzidos
- `transaction.completed` — após pagamento aprovado
- `transaction.failed` — após falha no processamento
- `transaction.refunded` — após estorno processado

### Tabelas do banco (schema: payment_service)

| Tabela | Propósito |
|--------|-----------|
| `transactions` | Registro de todas as transações (aprovadas e rejeitadas) |
| `refunds` | Registro de estornos por transação |
| `audit_logs` | Trilha imutável de todas as operações (PCI DSS) |
| `mp_test_accounts` | Credenciais das contas de teste MP (seller + buyer) |

### Chaves Redis

| Key | TTL | Propósito |
|-----|-----|-----------|
| `idempotency:payment:{uuid}` | 24h | Deduplicação de transações |
| `rate_limit:payment:{customerId}` | 1min | Rate limiting |
| `transaction:{id}` | 60s | Cache de leitura |

---

## Estrutura de Pacotes

```
src/main/java/com/acaboumony/payment/
├── controller/
│   └── TransactionController.java
├── service/
│   ├── TransactionService.java
│   └── RefundService.java
├── repository/
│   ├── TransactionRepository.java
│   └── RefundRepository.java
├── domain/
│   ├── entity/
│   │   ├── Transaction.java
│   │   └── Refund.java
│   └── enums/
│       ├── TransactionStatus.java
│       └── RefundReason.java
├── dto/
│   ├── request/
│   │   ├── TransactionRequest.java      (Record)
│   │   └── RefundRequest.java           (Record)
│   └── response/
│       ├── TransactionResponse.java     (Record)
│       └── RefundResponse.java          (Record)
├── result/
│   └── TransactionResult.java           (sealed interface)
├── exception/
│   ├── DuplicateIdempotencyKeyException.java
│   ├── CardDeclinedException.java
│   ├── FraudDetectedException.java
│   └── GatewayTimeoutException.java
├── config/
│   ├── MercadoPagoConfig.java
│   ├── RedisConfig.java
│   └── MpEncryptionConfig.java
├── domain/
│   └── enums/
│       └── MpAccountType.java           (SELLER, BUYER)
├── client/
│   ├── FraudServiceClient.java          (REST client para fraud-service)
│   └── MercadoPagoGateway.java          (adapter do SDK MP)
├── event/
│   ├── TransactionEventProducer.java    (Kafka producer)
│   └── MercadoPagoWebhookConsumer.java  (recebe webhooks do MP)
└── mapper/
    └── TransactionMapper.java
```

---

## Flyway Migrations

| Versão | Arquivo | Conteúdo |
|--------|---------|---------|
| V1 | `V1__create_transactions.sql` | Tabela transactions, índices |
| V2 | `V2__create_refunds.sql` | Tabela refunds, FK para transactions |
| V3 | `V3__create_audit_logs.sql` | Tabela audit_logs |
| V6 | `V6__create_mp_test_accounts.sql` | Tabela mp_test_accounts para credenciais MP |

---

## Integração Mercado Pago

### Configuração do SDK

```java
MercadoPagoConfig.setAccessToken(env.getProperty("mercadopago.access-token"));
```

### Criar pagamento

```java
PaymentCreateRequest request = PaymentCreateRequest.builder()
    .transactionAmount(new BigDecimal(amountInCents).divide(new BigDecimal(100)))
    .token(cardToken)
    .description("Acabou o Mony - Pedido " + orderId)
    .installments(installments)
    .paymentMethodId(paymentMethodId)
    .payer(PayerRequest.builder().email(customerEmail).build())
    .build();

Payment payment = paymentClient.create(request);
```

### Status mapping MP → interno

| Status MP | Status interno |
|-----------|---------------|
| approved | APPROVED |
| rejected | DECLINED |
| in_process | PROCESSING |
| cancelled | CANCELLED |

### Webhook do MP (receber notificações)

- Endpoint: `POST /api/v1/webhooks/mercadopago`
- Validar assinatura `x-signature` header
- Processar atualizações de status assincronamente

---

## Estratégia de Testes

| Tipo | Framework | Cenários |
|------|-----------|----------|
| Unitário | JUnit 5 + Mockito | TransactionService (todos os casos extremos da spec) |
| Integração | Testcontainers (PostgreSQL + Redis + Kafka) | Fluxo completo sem chamadas externas |
| Gateway mock | WireMock | Simular respostas do Mercado Pago (approved, rejected, timeout) |
| Fraud mock | Mockito | Simular respostas do fraud-service |

---

## Configuração Docker Compose

```yaml
payment-service:
  image: acaboumony/payment-service:latest
  ports:
    - "8082:8082"
  environment:
    - SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/payment_db
    - SPRING_REDIS_HOST=redis
    - SPRING_KAFKA_BOOTSTRAP_SERVERS=kafka:9092
    - MERCADOPAGO_ACCESS_TOKEN=${MERCADOPAGO_ACCESS_TOKEN}
    - FRAUD_SERVICE_URL=http://fraud-service:8085
    - NEW_RELIC_LICENSE_KEY=${NEW_RELIC_LICENSE_KEY}
    - NEW_RELIC_APP_NAME=acaboumony-payment-service
  depends_on:
    - postgres
    - redis
    - kafka
    - fraud-service
```

---

## Riscos

| Risco | Probabilidade | Impacto | Mitigação |
|-------|--------------|---------|-----------|
| MP API instável | Média | Alto | Circuit breaker + retry com backoff |
| Redis indisponível | Baixa | Médio | Fallback para DB para idempotência (mais lento) |
| fraud-service lento | Média | Alto | Timeout 250ms + score fallback = 50 |
| Double charge em retry | Baixa | Crítico | Idempotência via Redis + MP idempotency key header |
| Credenciais MP ausentes | Alta | Baixo | Fallback para token global (Fase 1) |
| OAuth password grant não suportado | Média | Médio | Fallback para authorization_code flow semi-automatizado |
