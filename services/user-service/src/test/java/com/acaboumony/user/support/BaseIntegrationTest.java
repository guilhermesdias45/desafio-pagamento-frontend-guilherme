package com.acaboumony.user.support;

import com.acaboumony.user.UserServiceApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Abstract base class for all integration tests.
 *
 * <p>Starts a single PostgreSQL 16, Redis 7, and Kafka instance per JVM using the Testcontainers
 * singleton pattern ({@code @Container static}). Subclasses automatically inherit the running
 * containers and the Spring context configured against them — no additional {@code @SpringBootTest}
 * or {@code @Testcontainers} annotations are needed on subclasses.</p>
 *
 * <p>JWT/TOTP/internal-secret properties are seeded with dummy values via
 * {@link #registerProperties}. Tasks that need real RSA keys (e.g. task-11) override with
 * {@code @TestPropertySource}.</p>
 */
@SpringBootTest(
        classes = UserServiceApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@ActiveProfiles("test")
@Testcontainers
public abstract class BaseIntegrationTest {

    private static final String TEST_PRIVATE_KEY_B64;
    private static final String TEST_PUBLIC_KEY_B64;

    static {
        try (InputStream privIs = BaseIntegrationTest.class.getResourceAsStream("/test-keys/test-private-key.pem");
             InputStream pubIs  = BaseIntegrationTest.class.getResourceAsStream("/test-keys/test-public-key.pem")) {
            TEST_PRIVATE_KEY_B64 = new String(Objects.requireNonNull(privIs).readAllBytes(), StandardCharsets.UTF_8).strip();
            TEST_PUBLIC_KEY_B64  = new String(Objects.requireNonNull(pubIs).readAllBytes(), StandardCharsets.UTF_8).strip();
        } catch (Exception e) {
            throw new RuntimeException("Failed to load test RSA keys from classpath", e);
        }
    }

    @Container
    public static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("user_db")
                    .withUsername("aom")
                    .withPassword("test")
                    .withReuse(true);

    @Container
    @SuppressWarnings("resource")
    public static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:7-alpine")
                    .withExposedPorts(6379)
                    .withCommand("redis-server", "--requirepass", "test")
                    .withReuse(true);

    @Container
    public static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.0"))
                    .withReuse(true);

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        // Database
        registry.add("spring.datasource.url",      POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username",  POSTGRES::getUsername);
        registry.add("spring.datasource.password",  POSTGRES::getPassword);

        // Redis
        registry.add("spring.data.redis.host",     REDIS::getHost);
        registry.add("spring.data.redis.port",     () -> REDIS.getMappedPort(6379));
        registry.add("spring.data.redis.password", () -> "test");

        // Kafka
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);

        // Real RSA test keys — loaded from test classpath so JwtTokenProvider/Validator start correctly.
        registry.add("jwt.private-key",  () -> TEST_PRIVATE_KEY_B64);
        registry.add("jwt.public-key",   () -> TEST_PUBLIC_KEY_B64);
        registry.add("totp.aes-key",     () -> "0".repeat(64));
        registry.add("totp.issuer",      () -> "AcabouoMonyTest");
        registry.add("internal.secret",  () -> "test-internal-secret");
    }
}
