# Sprint 1 + 2 — Dev 3: Relatório de Implementação

**Responsável:** Dev 3
**Serviços:** `order-service` (porta 8083), `notification-service` (porta 8084)
**Stack:** Java 21, Spring Boot 3.4.5, PostgreSQL 16, Redis 7, Kafka 3.7, Thymeleaf
**Status:** Sprint 1 completo, Sprint 2 completo

---

## Sumário

1. order-service: Sprint 1 — CRUD de Pedidos
2. order-service: Sprint 2 — Eventos Kafka e Expiração
3. notification-service: Sprint 2 — Emails Transacionais
4. Decisões Técnicas
5. Testes e Cobertura
6. Configuração de Ambiente
7. Gaps e Problemas Conhecidos
8. Recomendações para Sprint 3

---

## 1. order-service: Sprint 1 — CRUD de Pedidos

### Visão Geral

Serviço responsável pelo ciclo de vida dos pedidos. Um pedido representa a intenção de compra e precisa existir **antes** do pagamento. O payment-service referencia o orderId ao processar uma transação.

### Entidades de Domínio

| Classe | Arquivo | Tabela | Descrição |
|--------|---------|--------|-----------|
| `Order` | `domain/entity/Order.java` | `order_service.orders` | Cabeçalho do pedido com status, total, timestamps |
| `OrderItem` | `domain/entity/OrderItem.java` | `order_service.order_items` | Itens do pedido (FK orders com cascade ALL) |
| `OrderStatus` | `domain/enums/OrderStatus.java` | — | Enum: PENDING, PROCESSING, PAID, CANCELLED, REFUNDED, PARTIALLY_REFUNDED |

**Order — fields principais:**
- `id` (UUID, PK, gen_random_uuid)
- `customerId` (UUID — extraído do JWT, nunca do body)
- `merchantId` (UUID — vendedor)
- `status` (OrderStatus — default PENDING)
- `totalInCents` (Long — calculado no servidor)
- `transactionId` (String, nullable — preenchido quando pago)
- `idempotencyKey` (UUID, unique — deduplicação)
- `expiresAt` (Instant, nullable — 15 min após criação)

**OrderItem — fields:**
- `productId` (String, @NotBlank)
- `description` (String, max 255)
- `quantity` (Integer, 1–999)
- `unitPriceInCents` (Long, 1–999.999)
- `subtotalInCents` (Long — calculado: quantity unitPriceInCents)

### Ciclo de Vida do Pedido

```
PENDING -> PROCESSING -> PAID
   |                       |
   v                       v
CANCELLED              REFUNDED (total)
                       PARTIALLY_REFUNDED (parcial)
```

### Repositories

**OrderRepository** — JpaRepository<Order, UUID> com queries customizadas:
- `findByIdempotencyKey(UUID)` — busca por chave de idempotência
- `findByCustomerId(UUID, Pageable)` — pedidos do cliente (paginado)
- `findByMerchantId(UUID, Pageable)` — pedidos do merchant (paginado)
- `findByCustomerIdAndStatus(UUID, OrderStatus, Pageable)` — filtrado
- `findByMerchantIdAndStatus(UUID, OrderStatus, Pageable)` — filtrado
- `findByStatusAndExpiresAtBefore(OrderStatus, Instant)` — para o job de expiração

**OrderItemRepository** — apenas JpaRepository (gerenciado via cascade do Order)

### OrderService

Métodos públicos:

| Método | Escopo | Descrição |
|--------|--------|-----------|
| `createOrder(customerId, idempotencyKey, request)` | Transactional | Cria pedido PENDING, calcula total, verifica idempotência via Redis, publica order.created. Retorna sealed interface CreateOrderResult (Success ou Duplicate) |
| `getOrder(orderId, userId, role, merchantId)` | read-only | Com autorização: CUSTOMER ver proprios, MERCHANT ver seus, ADMIN ver todos |
| `listOrders(userId, role, merchantId, statusFilter, page, size)` | read-only | Paginado (max 100/page), filtro por status |
| `cancelOrder(orderId, userId, role, merchantId)` | Transactional | Cancela apenas se PENDING |

Validações internas:
- `validateItems()` — preco 1–999.999, quantidade 1–999
- `calculateTotal()` — soma unitPriceInCents quantity, maximo 999.999 centavos
- `authorizeAccess()` — CUSTOMER/MERCHANT dono, ADMIN sempre passa

### IdempotencyService

Redis-backed, TTL 24h. Key pattern: `idempotency:order:{uuid}`.

- `isDuplicate(key)` — true se existe no Redis
- `markProcessed(key)` / `markProcessed(key, orderId)` — marca como processado
- `getExistingOrderId(key)` — retorna orderId armazenado

### Controllers

| Endpoint | Headers Necessarios | Status |
|----------|--------------------|--------|
| POST /api/v1/orders | X-User-Id, Idempotency-Key | 201 (novo) / 200 (duplicata) |
| GET /api/v1/orders/{orderId} | X-User-Id, X-User-Roles, X-Merchant-Id | 200 |
| GET /api/v1/orders | idem | 200 |
| DELETE /api/v1/orders/{orderId} | idem | 204 |

### GlobalExceptionHandler

| Excecao | HTTP | Codigo |
|---------|------|--------|
| OrderNotFoundException | 404 | ORDER_NOT_FOUND |
| OrderCannotBeCancelledException | 422 | ORDER_CANNOT_BE_CANCELLED |
| InsufficientPermissionsException | 403 | INSUFFICIENT_PERMISSIONS |
| EmptyOrderException | 400 | EMPTY_ORDER |
| InvalidItemPriceException | 400 | INVALID_ITEM_PRICE |
| InvalidQuantityException | 400 | INVALID_QUANTITY |
| OrderTotalExceedsLimitException | 400 | TOTAL_EXCEEDS_LIMIT |
| MethodArgumentNotValidException | 400 | INVALID_FIELD |
| Exception (catch-all) | 500 | INTERNAL_ERROR |

### DTOs

- **CreateOrderRequest:** merchantId (UUID @NotNull), items (List @NotEmpty @Valid)
- **ItemRequest:** productId (@NotBlank), description (@Size max 255), quantity (@Min 1 @Max 999), unitPriceInCents (@Min 1 @Max 999999)
- **ApiResponse<T>:** Envelope padrao com data, meta (timestamp, requestId), errors (lista ErrorDetail com code, message, field, retryable)
- **OrderResponse:** orderId, status, totalInCents, items, expiresAt, createdAt
- **OrderDetailResponse:** + customerId, merchantId, transactionId, updatedAt
- **PagedResponse:** content, page, size, totalElements, totalPages

### Flyway

- V1: schema order_service, tabela orders com indices, check constraint status
- V2: tabela order_items com FK, indice em order_id

---

## 2. order-service: Sprint 2 — Eventos Kafka e Expiração

### Kafka Producer

| Classe | Topico | Evento | Quando |
|--------|--------|--------|--------|
| OrderEventProducer | order.created | OrderCreatedEvent | Apos criar pedido |

**OrderCreatedEvent (record):** orderId, customerId, merchantId, totalInCents, items (List OrderItemEvent), createdAt

### Kafka Consumer

**TransactionEventConsumer** — grupo: order-service-group, config: earliest, read_committed

| Topico | Acao |
|--------|------|
| transaction.completed | Status -> PAID, grava transactionId, limpa expiresAt |
| transaction.failed | Status -> PENDING (se PROCESSING ou PENDING) |
| transaction.refunded | Status -> REFUNDED (full) ou PARTIALLY_REFUNDED (parcial) |

### OrderExpirationService

```java
@Scheduled(fixedDelay = 60, TimeUnit.SECONDS)
public void expireStaleOrders()
```

- Executa a cada 60s
- Busca PENDING com expiresAt < now()
- Atualiza para CANCELLED, limpa expiresAt
- **Gap:** Nao publica order.cancelled no Kafka (spec CE-002)

---

## 3. notification-service: Sprint 2 — Emails Transacionais

Servico **exclusivamente orientado a eventos** — nenhum endpoint REST. Consome 8 topicos Kafka e envia emails via SMTP (Thymeleaf).

### Eventos e Emails

| Topico | Consumer | Template | Destinatario |
|--------|----------|----------|-------------|
| user.registered | UserEventConsumer | welcome | Email do usuario |
| user.login.blocked | UserEventConsumer | login-blocked | Email do usuario |
| user.2fa.enabled | UserEventConsumer | 2fa-enabled | Email do usuario |
| order.created | OrderEventConsumer | order-created | **null (gap)** |
| transaction.completed | TransactionEventConsumer | payment-confirmed-customer | Cliente |
| | | payment-confirmed-merchant | Merchant |
| transaction.failed | TransactionEventConsumer | payment-failed | Cliente |
| transaction.refunded | TransactionEventConsumer | refund-confirmed | Cliente |
| fraud.detected | FraudEventConsumer | fraud-alert | SECURITY_ALERT_EMAIL |

### EmailService (core)

```java
void sendEmail(String to, String subject, String templateName,
               Map<String, Object> variables, String correlationId)
```

Pipeline:
1. Null/blank guard -> se null/blank, loga warning e retorna
2. Idempotencia -> busca notification_log por correlationId + eventType; se existe, loga e retorna
3. Send with retry -> 3 tentativas com backoff exponencial (1s, 5s, 30s)
4. Sucesso -> notification_log status SENT
5. Falha -> notification_log status FAILED + EmailDeliveryException

### Retry

- 3 tentativas, backoff: 1s -> 5s -> 30s
- Thread.sleep (aceitavel com virtual threads)
- Retryaveis: MailException, MessagingException
- Nao retryavel (DLQ): IllegalArgumentException

### Event DTOs (todos Java records)

| Evento | Campos |
|--------|--------|
| UserRegisteredEvent | userId, email, fullName, role, confirmationToken, registeredAt |
| UserLoginBlockedEvent | userId, email, blockedAt, unlockAt, ipAddress, attemptCount |
| User2faEnabledEvent | userId, email, fullName, enabledAt |
| OrderCreatedEvent | orderId, customerId, merchantId, totalInCents, items, createdAt |
| TransactionCompletedEvent | transactionId, mpPaymentId, orderId, customerId, merchantId, customerEmail, merchantEmail, amountInCents, currency, cardBrand, cardLastFour, installments, items |
| TransactionFailedEvent | transactionId, orderId, customerId, customerEmail, amountInCents, reason, createdAt |
| TransactionRefundedEvent | refundId, transactionId, orderId, customerEmail, amountRefundedInCents, isFullRefund, reason, estimatedArrivalDays, refundedAt |
| FraudDetectedEvent | transactionId, customerId, score, decision, reasons, detectedAt |

### Templates de Email (9 Thymeleaf HTML)

| Template | Assunto |
|----------|---------|
| welcome.html | Bem-vindo(a) a Acabou o Mony! Confirme seu email |
| login-blocked.html | Acesso a sua conta bloqueado temporariamente |
| 2fa-enabled.html | Autenticacao de dois fatores ativada |
| order-created.html | Pedido confirmado — #{orderId} |
| payment-confirmed-customer.html | Pagamento confirmado |
| payment-confirmed-merchant.html | Nova venda confirmada |
| payment-failed.html | Pagamento nao aprovado |
| refund-confirmed.html | Estorno processado |
| fraud-alert.html | [FRAUD ALERT] Score {score} |

Todos em pt-BR, CSS inline, sem PII.

### KafkaConsumerConfig

- DefaultErrorHandler com FixedBackOff(1000L, 3)
- IllegalArgumentException como non-retryable
- Grupo: notification-service-group

### NotificationLog (entidade)

- id (UUID PK), eventType, recipientEmail, status (SENT/FAILED), correlationId, errorMessage, createdAt
- Indices: event_type, recipient_email, status, correlation_id

---

## 4. Decisoes Tecnicas

| Decisao | Escolha | Motivo |
|---------|---------|--------|
| Calculo do total | Server-side | Seguranca — cliente nao manipula valor |
| Expiracao de pedidos | @Scheduled (60s) | Mais simples que Kafka delayed event |
| Atualizacao de status | Kafka consumer (async) | Desacoplamento do payment-service |
| Idempotencia criacao | Redis TTL 24h | Consistente com outros servicos |
| Idempotencia email | notification_log (DB) | Historico auditavel |
| Template engine | Thymeleaf | Built-in Spring Boot |
| SMTP | JavaMailSender | Agnostic ao provedor |
| Retry email | 3x backoff (1s, 5s, 30s) | Simples, Kafka ja tem retry |
| Virtual threads | spring.threads.virtual.enabled: true | Melhor throughput I/O |
| Schema DB | ddl-auto=validate + Flyway | Previne drift codigo-DB |
| Isolamento DB | Schema por servico | Ownership claro |

### Padroes

- Injecao por construtor (sem @Autowired)
- DTOs sao Java records (imutaveis)
- Sealed interface para resultados
- @RestControllerAdvice para erros centralizados
- Autorizacao por valor (role headers do api-gateway)
- Cascade persist (OrderItem via Order)
- Sem Lombok em producao

---

## 5. Testes e Cobertura

### order-service — 66 testes, JaCoCo 90%+

| Test Class | Tests | Cenarios |
|-----------|-------|----------|
| OrderServiceTest | 27 | Criacao (12), consulta (6), listagem (4), cancelamento (5) |
| TransactionEventConsumerTest | 9 | Completed (3), failed (3), refunded (3) |
| OrderExpirationServiceTest | 3 | Expira, no-op sem stale, lista vazia |
| IdempotencyServiceTest | 7 | isDuplicate, markProcessed, getExistingOrderId |
| OrderControllerTest | 11 | 201/200/404/403/422/400 |
| GlobalExceptionHandlerTest | 9 | Todos os codigos de erro |

### notification-service — 20 testes, JaCoCo 90%+

| Test Class | Tests | Cenarios |
|-----------|-------|----------|
| EmailServiceTest | 6 | Send (4): sucesso, duplicata, retry 3x, max retries; Idempotencia (2) |
| EmailServiceNullRecipientTest | 2 | Null/blank recipient -> skip |
| UserEventConsumerTest | 3 | welcome, login-blocked, 2fa-enabled |
| TransactionEventConsumerTest | 3 | 2 emails, failed, refunded |
| OrderEventConsumerTest | 2 | Processamento, items vazios |
| FraudEventConsumerTest | 2 | Security email configurado/vazio |
| KafkaConsumerConfigTest | 2 | ErrorHandler, factory |

### Frameworks

- JUnit 5 + Mockito (unitarios)
- MockMvc @WebMvcTest (controllers)
- Testcontainers declarado mas nao utilizado
- GreenMail declarado mas nao utilizado

---

## 6. Configuracao de Ambiente

### order-service (.env)

```
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/order_db
SPRING_DATA_REDIS_HOST=localhost
SPRING_KAFKA_BOOTSTRAP_SERVERS=localhost:9094
ORDER_EXPIRATION_MINUTES=15
SERVER_PORT=8083
```

### notification-service (.env)

```
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/notification_db
SPRING_KAFKA_BOOTSTRAP_SERVERS=localhost:9094
SMTP_HOST=localhost
SMTP_PORT=3025
SMTP_USERNAME=
SMTP_PASSWORD=
SECURITY_ALERT_EMAIL=security@acaboumony.com
APP_BASE_URL=https://app.acaboumony.com
SERVER_PORT=8084
```

### JaCoCo

- Plugin: jacoco-maven-plugin:0.8.12
- Minimo: 90% line coverage (fase check)
- Exclusoes: entities, DTOs, Application class, config, exceptions

---

## 7. Gaps e Problemas Conhecidos

### Alta Prioridade

| # | Servico | Problema | Impacto |
|---|---------|----------|---------|
| 1 | notification | OrderEventConsumer envia email para null | Cliente nunca recebe confirmacao do pedido |
| 2 | order | OrderExpirationService nao publica order.cancelled | notification-service nao sabe que pedido expirou |
| 3 | ambos | Sem testes de integracao (Testcontainers + GreenMail) | Risco de quebra em producao |

### Media Prioridade

| # | Servico | Problema | Impacto |
|---|---------|----------|---------|
| 4 | notification | DLQ nao configurada explicitamente | Eventos que falham 3x nao vao para topico DLQ |
| 5 | notification | Rate limiting nao implementado | Pode exceder limite do SMTP |
| 6 | notification | Template corrompido sem fallback para texto | Se template quebra, email nao enviado |
| 7 | order | OrderEventProducer sem teste unitario direto | 16% coverage na classe |
| 8 | order | listOrders para ADMIN com statusFilter usa customerId=null | Pode filtrar incorretamente |

### Baixa Prioridade

| # | Servico | Problema |
|---|---------|----------|
| 9 | order | MapStruct no pom mas nao usado |
| 10 | notification | MailConfig.java nao existe (auto-config OK) |

---

## 8. Recomendacoes para Sprint 3

O TEAM.md original lista Sprint 3 como "notification-service: todos os 8 tipos de email com templates HTML" — mas isso ja foi implementado no Sprint 2. Sugere-se redefinir Sprint 3 da seguinte forma:

### Fixes de Alta Prioridade

1. **OrderEventConsumer enviar email real** — Adicionar campo `customerEmail` ao `OrderCreatedEvent` no order-service (extraido do header `X-User-Email` do JWT) para que o notification-service possa enviar o email de confirmacao

2. **OrderExpirationService publicar order.cancelled** — Adicionar Kafka producer + evento no order-service quando pedido expirar

### Resiliência e Integração

3. **Testes de integracao com Testcontainers + GreenMail** para ambos os servicos:
   - order-service: POST /orders -> Kafka order.created -> Kafka transaction.completed -> status PAID
   - notification-service: evento Kafka -> EmailService -> GreenMail SMTP -> notificacao recebida

4. **DLQ configurada** — Adicionar DeadLetterPublishingRecoverer no KafkaConsumerConfig

5. **Rate limiting** — 10 emails/hora por destinatario (spec 13)

6. **Template fallback** — Texto puro quando Thymeleaf falha (spec CE-004)

### Cleanup

7. Teste unitario para OrderEventProducer
8. Remover MapStruct se nao for usar
9. Cache de leitura Redis (plan.md menciona order:{id} TTL 60s)

### Fluxo Sugerido

```
1. Fix ordem de consumo do OrderEventConsumer (precisa do customerEmail no evento)
   -> Requer alteracao no OrderCreatedEvent do order-service
   -> OrderService.createOrder() extrair email do header X-User-Email

2. Fix OrderExpirationService publicar order.cancelled
   -> Novo producer + evento no order-service

3. Testes de integracao protegendo os fixes

4. Melhorias de resiliencia (DLQ, rate limiting, template fallback)

5. mvn verify (testes + JaCoCo >= 90%)
```

### Arquivos-Chave para Sprint 3

order-service:
- services/order-service/src/main/java/com/acaboumony/order/event/OrderEventProducer.java
- services/order-service/src/main/java/com/acaboumony/order/event/OrderCreatedEvent.java
- services/order-service/src/main/java/com/acaboumony/order/service/OrderExpirationService.java
- services/order-service/src/main/java/com/acaboumony/order/service/OrderService.java
- services/order-service/src/test/java/com/acaboumony/order/service/OrderServiceTest.java
- services/order-service/src/test/java/com/acaboumony/order/service/OrderExpirationServiceTest.java

notification-service:
- services/notification-service/src/main/java/com/acaboumony/notification/consumer/OrderEventConsumer.java
- services/notification-service/src/main/java/com/acaboumony/notification/service/EmailService.java
- services/notification-service/src/main/java/com/acaboumony/notification/config/KafkaConsumerConfig.java
- services/notification-service/src/test/java/com/acaboumony/notification/service/OrderEventConsumerTest.java
- services/notification-service/src/test/java/com/acaboumony/notification/service/EmailServiceTest.java
