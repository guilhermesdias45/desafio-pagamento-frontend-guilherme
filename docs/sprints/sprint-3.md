# Sprint 3 — Relatório de Progresso

**Responsável:** Dev 3
**Serviços:** `order-service` (porta 8083), `notification-service` (porta 8084)
**Período:** Sprint 3
**Status:** Concluído ✅

---

## O que foi feito

### 1. customerEmail no OrderCreatedEvent

| Antes | Depois |
|-------|--------|
| `OrderCreatedEvent` sem `customerEmail` | `customerEmail` adicionado ao record |
| Notification service enviava email para `null` | Agora envia para o email real do cliente |

**Alterações:**
- `OrderService.createOrder()` agora recebe `customerEmail` como parâmetro
- `OrderController` extrai `X-User-Email` do header (injetado pelo api-gateway)
- `OrderEventProducer.publishOrderCreated()` propaga `customerEmail` no evento
- Notification service: `OrderCreatedEvent` DTO atualizado, consumer usa `customerEmail` no lugar de `null`

### 2. OrderExpirationService publica order.cancelled

| Antes | Depois |
|-------|--------|
| Pedido expirava para `CANCELLED` sem notificar ninguém | Publica `OrderCancelledEvent` no Kafka |

**Artefatos criados:**
- `OrderCancelledEvent.java` (record) — `orderId, customerId, customerEmail, status, cancelledAt`
- `OrderEventProducer.publishOrderCancelled()` — método novo
- `OrderExpirationService` agora injeta `OrderEventProducer` e publica evento após expirar
- Notification service: `OrderCancelledEvent` DTO + `OrderEventConsumer.consumeOrderCancelled()`

### 3. DLQ configurada (notification-service)

- `KafkaConsumerConfig` com `DeadLetterPublishingRecoverer`
- Tópico DLQ: `{topic}.DLQ`
- Retry: 3 tentativas com `FixedBackOff(1000L)`
- `IllegalArgumentException` tratado como non-retryable (vai direto para DLQ)

### 4. Rate limiting (notification-service)

**`EmailRateLimiter`** — sliding window in-memory:
- Máximo configurável via `notification.email.rate-limit` (default: 10 emails/hora)
- Bucket por destinatário usando `ConcurrentHashMap`
- Cleanup automático a cada 5 minutos (remove buckets vazios)
- Thread-safe com `synchronized` no bucket

**Integração no `EmailService.sendEmail()`:**
- Se rate-limited, loga warning e retorna sem enviar
- Não conta emails para destinatários null/blank

### 5. Template fallback (notification-service)

- `EmailService.sendMimeMessage()` com try-catch no `processTemplate`
- Se Thymeleaf lança exceção, usa `buildPlainTextFallback()` com formato `chave: valor<br/>`
- Garante que o email sempre é enviado mesmo com template corrompido

### 6. Cache de leitura Redis (order-service)

**`OrderCacheService`** — cache-aside:
- Key: `order:{id}`, TTL: 60 segundos
- Read: busca Redis → miss → busca JPA → popula cache
- Evict: em `cancelOrder()`, em `getOrder()` (duplicate path), no `TransactionEventConsumer` após mudança de status
- `OrderService` injeta `OrderCacheService`, usa em `getOrder()` e `cancelOrder()`

### 7. Teste unitário para OrderEventProducer

- `OrderEventProducerTest.java` com 2 testes: `publishOrderCreated` e `publishOrderCancelled`
- Mock `KafkaTemplate` com `CompletableFuture.completedFuture()`

### 8. MapStruct removido (order-service)

- Dependência e annotation processor removidos do `pom.xml`

---

## Resultado dos Testes

### order-service — 68 testes, JaCoCo ≥ 90% ✅

| Test Class | Tests | Descrição |
|-----------|-------|-----------|
| `OrderServiceTest` | 27 | CRUD + autorização + customerEmail |
| `TransactionEventConsumerTest` | 9 | Completed + failed + refunded + cache evict |
| `OrderExpirationServiceTest` | 3 | Expira + no-op + producer chamado |
| `IdempotencyServiceTest` | 7 | Idempotência Redis |
| `OrderControllerTest` | 11 | Endpoints REST |
| `GlobalExceptionHandlerTest` | 9 | Erros centralizados |
| `OrderEventProducerTest` | 2 | Publish created + cancelled |

### notification-service — 29 testes, JaCoCo ≥ 90% ✅

| Test Class | Tests | Descrição |
|-----------|-------|-----------|
| `EmailServiceTest` | 7 | Send (4), idempotency (2), rate-limit (1) |
| `EmailServiceTest$TemplateFallback` | 1 | Fallback para texto puro |
| `EmailServiceNullRecipientTest` | 2 | Null/blank guard |
| `EmailRateLimiterTest` | 6 | Rate limiting unitário |
| `UserEventConsumerTest` | 3 | welcome, login-blocked, 2fa-enabled |
| `TransactionEventConsumerTest` | 3 | Completed, failed, refunded |
| `OrderEventConsumerTest` | 3 | Order created + cancelled |
| `FraudEventConsumerTest` | 2 | Fraud alert |
| `KafkaConsumerConfigTest` | 2 | ErrorHandler + DLQ |

---

## Decisões Técnicas do Sprint 3

| Decisão | Escolha | Motivo |
|---------|---------|--------|
| `customerEmail` no header | `X-User-Email` do api-gateway | Segurança — cliente não manipula email no body |
| Rate limiter in-memory | ConcurrentHashMap + cleanup | notification-service não tem dependência Redis |
| Template fallback | `buildPlainTextFallback()` com HTML | Mantém consistência visual mesmo sem template |
| Cache TTL | 60 segundos | Minimiza stale reads sem invalidar com frequência |
| Cache evict | Manual em pontos conhecidos | Mais previsível que TTL curto sozinho |

---

## Gaps Conhecidos

| # | Serviço | Problema | Prioridade |
|---|---------|----------|------------|
| 1 | order | `listOrders` para ADMIN com `statusFilter` usa `customerId=null` — pode filtrar incorretamente | Média |
| 2 | order | `listOrders` para MERCHANT com `statusFilter` usa `merchantId=null` — pode filtrar incorretamente | Média |
| 3 | ambos | Sem testes de integração com Testcontainers + GreenMail | Média |
| 4 | order | `TransactionEventConsumer` não usa cache para leitura ao atualizar status | Baixa |
| 5 | notification | `MailConfig.java` não existe (auto-config OK, mas sem customização SMTP) | Baixa |

---

## Sprint 4 — Planejamento

### Dev 3 — Pendências Técnicas

#### order-service

- [ ] **Fix `listOrders` para ADMIN** — Corrigir query quando ADMIN usa `statusFilter`; atualmente passa `customerId=null` na query `findByCustomerIdAndStatus`
- [ ] **Fix `listOrders` para MERCHANT** — Mesmo problema com `merchantId=null` em `findByMerchantIdAndStatus`
- [ ] **Cache no `TransactionEventConsumer`** — Usar `OrderCacheService` para leitura antes de atualizar status (evita race condition com JPA L1 cache)
- [ ] **Documentar API de Pedidos** — OpenAPI/Swagger ou `api-contracts.md` atualizado

#### notification-service

- [ ] **`MailConfig.java`** — Criar configuração explícita do `JavaMailSender` com pool de conexão e timeouts configuráveis
- [ ] **Monitoramento de rate limit** — Expor métricas no `/actuator/metrics` (emails enviados, rate-limited, falhas por destinatário)

#### Ambos

- [ ] **Testes de integração com Testcontainers + GreenMail**
  - Fluxo: `POST /orders` → Kafka `order.created` → notification-service → GreenMail → email recebido
  - Fluxo: Expiração → Kafka `order.cancelled` → notification-service → GreenMail → email recebido
- [ ] **Health checks customizados** — Indicadores de saúde para Redis, Kafka, SMTP

---

### Dev 3 — Novas Features (Sprint 3 Original)

Caso o roadmap original do Sprint 3 seja postergado para Sprint 4:

- [ ] **Webhook de Pedidos** — `POST /api/v1/orders/{orderId}/webhook` para notificar merchant sobre mudanças de status
- [ ] **WebSocket** — Notificações em tempo real para o cliente via WebSocket (status do pedido)
- [ ] **Relatório de Pedidos** — `GET /api/v1/orders/report` com filtros por período, status, valor

---

### Dev 1 e Dev 2 (Sugestões)

- [ ] **Dev 2:** Finalizar `user-service` (register, login, JWT, refresh, logout)
- [ ] **Dev 2:** Finalizar `api-gateway` (AuthenticationFilter, rate limiting, headers)
- [ ] **Dev 1:** Finalizar `fraud-service` (regras de scoring, velocity checks, blacklist)
- [ ] **Dev 1:** Finalizar `payment-service` (transações, Mercado Pago, webhook)

---

### Cronograma Sugerido

| Fase | O que | Dias |
|------|-------|------|
| 1 | Fix `listOrders` ADMIN/MERCHANT | 0.5 |
| 2 | Cache no `TransactionEventConsumer` | 0.5 |
| 3 | `MailConfig.java` + timeouts SMTP | 0.5 |
| 4 | Testes de integração (fluxo crítico) | 2 |
| 5 | Métricas + Health checks | 1 |
| 6 | Webhook/WebSocket/Relatório (se aplicável) | 2–3 |

**Total estimado:** 4–7 dias úteis
