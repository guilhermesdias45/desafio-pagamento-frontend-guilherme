# Task 10: BaseIntegrationTest — Testcontainers singleton abstract class

## Objective
Criar `BaseIntegrationTest.java` abstract class que sobe **um único** PostgreSQL 16, Redis 7 e Kafka KRaft 3.7 via Testcontainers, compartilhado entre todas as classes de teste de integração da JVM. Configura Spring properties dinamicamente para apontar para os containers. Todas as classes `*IT.java` estendem essa base.

## Context
**Quick Context:**
- Sem isso, cada classe de integração sobe seu próprio cluster — alto custo de tempo e RAM, contenção de portas em paralelismo.
- Padrão da indústria: `@Container static` em abstract class faz os containers serem únicos por classloader/JVM (Singleton pattern); subclasses herdam.
- Spring Boot 3.x suporta `@DynamicPropertySource` para registrar properties em runtime apontando para portas dinâmicas dos containers.
- **Este é um arquivo de test infrastructure**, não código de runtime — não conta para JaCoCo. Não há TDD formal **dele**; mas existe um smoke test que verifica que os containers sobem.

Ler antes:
- `specs/user-service/plan.md` §"Estratégia de Testes" (linhas 237-244)
- `services/user-service/pom.xml` (verificar deps de Testcontainers: `junit-jupiter`, `postgresql`, `kafka`)
- `tasks/dev2/shared-context.md` §"Test Infrastructure"

## Target Files
**Create:**
- `services/user-service/src/test/java/com/acaboumony/user/support/BaseIntegrationTest.java`
- `services/user-service/src/test/java/com/acaboumony/user/support/BaseIntegrationTestSmokeIT.java`

## Dependencies
- Depends on: task-02 (application.yml com defaults), task-03 (V1) — para subir o Flyway sem erro
- Blocks: task-08 (repositories IT), task-12 (UserEventProducer IT), task-13 (LoginAttemptService IT), task-14..task-24 (qualquer IT)

## Requirements

### BaseIntegrationTest.java
```java
@SpringBootTest(classes = UserServiceApplication.class)
@ActiveProfiles("test")
@Testcontainers
public abstract class BaseIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("user_db")
        .withUsername("aom")
        .withPassword("test")
        .withReuse(true);

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine")
        .withExposedPorts(6379)
        .withCommand("redis-server", "--requirepass", "test")
        .withReuse(true);

    @Container
    static final KafkaContainer KAFKA = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.0"))
        .withReuse(true);

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.data.redis.password", () -> "test");
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        // valores dummy para JWT/TOTP/INTERNAL — tasks específicas que precisam de chaves reais sobreescrevem via @TestPropertySource
        registry.add("jwt.private-key", () -> "test-private-key-base64-dummy");
        registry.add("jwt.public-key", () -> "test-public-key-base64-dummy");
        registry.add("totp.aes-key", () -> "0".repeat(64));
        registry.add("totp.issuer", () -> "AcabouoMonyTest");
        registry.add("internal.secret", () -> "test-internal-secret");
    }
}
```

### Notas
- `@Container static` com `withReuse(true)` — habilita reutilização entre execuções se `~/.testcontainers.properties` tiver `testcontainers.reuse.enable=true` (otimização opcional, não obrigatória).
- Profile `test` precisa existir em `application-test.yml` apenas com `spring.flyway.enabled: true`, `spring.jpa.hibernate.ddl-auto: validate`. Criar esse arquivo nesta task.
- Usar `KafkaContainer` da Testcontainers (confluentinc/cp-kafka) — é o suporte oficial mais estável. NÃO usar bitnami/kafka em Testcontainers (não tem wrapper).

### BaseIntegrationTestSmokeIT.java
Smoke test que estende a base e verifica:
- `deve_subir_postgres_redis_e_kafka_quando_spring_context_carrega()` — `@Autowired DataSource`, `@Autowired StringRedisTemplate`, `@Autowired KafkaTemplate`. Asserta que conexões funcionam (executa `SELECT 1`, ping no Redis, sendo um envio dummy ao Kafka num topic auto-criado em modo test).

## Acceptance Criteria
- [ ] `BaseIntegrationTest` é uma classe abstract com PostgreSQL, Redis, Kafka como `@Container static`
- [ ] `@DynamicPropertySource` registra URLs/portas para Spring
- [ ] `application-test.yml` criado com config mínima (flyway enabled, ddl-auto validate)
- [ ] `BaseIntegrationTestSmokeIT` passa (`./mvnw test -Dtest=BaseIntegrationTestSmokeIT`)
- [ ] Containers são compartilhados entre múltiplas classes IT na mesma execução do Maven (verificar via logs — `Container postgres:16-alpine started` aparece UMA vez por JVM, não por classe)
- [ ] Subclasses não precisam declarar `@Testcontainers` ou `@SpringBootTest` — herdam tudo
