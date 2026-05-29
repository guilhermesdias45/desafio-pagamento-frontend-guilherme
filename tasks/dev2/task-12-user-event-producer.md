# Task 12: UserEventProducer (Kafka — tópico único user-events)

## Objective
Implementar produtor Kafka que publica eventos do user-service no tópico único `user-events`. Cada evento contém `eventType` (`user.registered`, `user.login.success`, `user.login.blocked`, `user.2fa.enabled`) e payload específico do tipo.

## Context
**Quick Context:**
- Tópico único `user-events` foi criado em task-01 (kafka-init).
- Spring Kafka producer já configurado em `application-docker.yml`: `key-serializer: StringSerializer`, `value-serializer: JsonSerializer`.
- Key da mensagem = `userId.toString()` (garante ordenação por usuário dentro de uma partição).
- Eventos são records imutáveis em pacote `event.payload` — payload serializado como JSON.

Ler antes:
- `specs/user-service/plan.md` §"Tópicos Kafka produzidos" (linhas 52-58)
- `specs/user-service/spec.md` §3.4 (evento user.registered), §4.6 (user.login.blocked), §6.5 (user.2fa.enabled)
- `services/user-service/src/main/resources/application-docker.yml` (config producer)
- `tasks/dev2/task-01-docker-compose-env.md` (confirma tópico `user-events`)

## Target Files
**Create:**
- `services/user-service/src/main/java/com/acaboumony/user/event/UserEventProducer.java`
- `services/user-service/src/main/java/com/acaboumony/user/event/UserEvent.java` (sealed interface)
- `services/user-service/src/main/java/com/acaboumony/user/event/payload/UserRegisteredEvent.java` (Record)
- `services/user-service/src/main/java/com/acaboumony/user/event/payload/UserLoginSuccessEvent.java`
- `services/user-service/src/main/java/com/acaboumony/user/event/payload/UserLoginBlockedEvent.java`
- `services/user-service/src/main/java/com/acaboumony/user/event/payload/UserTwoFactorEnabledEvent.java`
- `services/user-service/src/test/java/com/acaboumony/user/event/UserEventProducerIT.java`

## Dependencies
- Depends on: task-01, task-07 (UserRole), task-10 (BaseIntegrationTest)
- Blocks: task-14, task-15, task-16

## TDD Mode

### RED
`UserEventProducerIT extends BaseIntegrationTest` (precisa de Kafka real):
- `deve_publicar_user_registered_no_topico_user_events_quando_publishUserRegistered()` — consumir via `KafkaConsumer` test, assert payload tem `eventType = "user.registered"`, `userId`, `email`, `role`, `merchantId`.
- `deve_publicar_user_login_blocked_com_unlockAt_quando_publishLoginBlocked()`.
- `deve_publicar_user_login_success_quando_publishLoginSuccess()`.
- `deve_publicar_user_2fa_enabled_quando_publishTwoFactorEnabled()`.
- `deve_usar_userId_como_key_da_mensagem_quando_publish_qualquer_tipo()` — verifica que `ConsumerRecord.key()` == userId.

Roda → falha.

### GREEN
1. **`UserEvent` sealed interface:**
   ```java
   public sealed interface UserEvent permits
       UserRegisteredEvent, UserLoginSuccessEvent, UserLoginBlockedEvent, UserTwoFactorEnabledEvent {
       UUID userId();
       String eventType();  // ex: "user.registered"
       Instant occurredAt();
   }
   ```
2. **Record payloads** (cada um implementa `UserEvent`):
   - `UserRegisteredEvent(UUID userId, String email, UserRole role, UUID merchantId, Instant occurredAt)` — `eventType()` retorna `"user.registered"`.
   - `UserLoginSuccessEvent(UUID userId, String email, String deviceFingerprint, Instant occurredAt)` — `"user.login.success"`.
   - `UserLoginBlockedEvent(UUID userId, String email, Instant unlockAt, Instant occurredAt)` — `"user.login.blocked"`.
   - `UserTwoFactorEnabledEvent(UUID userId, Instant occurredAt)` — `"user.2fa.enabled"`.
3. **`UserEventProducer`** — `@Component`, injeta `KafkaTemplate<String, UserEvent>`. Constante `TOPIC = "user-events"`. Métodos:
   - `publish(UserEvent event)` — `kafkaTemplate.send(TOPIC, event.userId().toString(), event)`.
   - Métodos convenience: `publishUserRegistered(UUID userId, ...)` etc, delegando a `publish(...)`.
   - **Não bloquear**: `.send()` retorna `CompletableFuture<SendResult>` — logar erro no `.whenComplete(...)` sem bloquear thread chamadora (Virtual Threads OK, mas evitar `.get()` desnecessário).

### REFACTOR
- Logging: `logger.debug("Published {} for userId={}", event.eventType(), event.userId())`.
- Verificar que payload JSON inclui `eventType` (Jackson serializa automaticamente o método; ou usar `@JsonProperty("eventType")` se necessário).
- Considerar `KafkaTemplate.send(ProducerRecord)` com headers (ex: `event-type` no header também) — não obrigatório agora.

## Acceptance Criteria
- [ ] `UserEventProducerIT` passa (5 testes verdes)
- [ ] Eventos publicados no tópico `user-events` (não em tópicos separados)
- [ ] Payload JSON inclui `eventType` discriminator (deserializável por consumidor)
- [ ] Key da mensagem = `userId.toString()` em todos os 4 tipos
- [ ] `UserEvent` é uma `sealed interface` com exatamente 4 `permits`
- [ ] Producer não bloqueia thread chamadora (uso de `CompletableFuture`)
- [ ] Logs nunca incluem campos sensíveis (não há campos sensíveis nos events — payload é seguro)
