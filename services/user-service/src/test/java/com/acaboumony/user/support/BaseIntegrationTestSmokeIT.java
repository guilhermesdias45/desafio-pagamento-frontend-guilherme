package com.acaboumony.user.support;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

/**
 * Smoke test: verifies that PostgreSQL, Redis, and Kafka containers are all reachable
 * after the Spring context loads.
 */
class BaseIntegrationTestSmokeIT extends BaseIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Test
    void deve_subir_postgres_redis_e_kafka_quando_spring_context_carrega() throws Exception {
        // PostgreSQL: execute SELECT 1
        try (Connection conn = dataSource.getConnection();
             ResultSet rs = conn.createStatement().executeQuery("SELECT 1")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt(1)).isEqualTo(1);
        }

        // Redis: write and read a key
        stringRedisTemplate.opsForValue().set("smoke-test-key", "smoke-test-value");
        String value = stringRedisTemplate.opsForValue().get("smoke-test-key");
        assertThat(value).isEqualTo("smoke-test-value");
        stringRedisTemplate.delete("smoke-test-key");

        // Kafka: send a message to a test topic (auto-created in test mode)
        assertThatNoException().isThrownBy(() ->
                kafkaTemplate.send("smoke-test-topic", "smoke-key", "smoke-value").get()
        );

        // Confirm containers are shared (logged once per JVM, not per test class)
        assertThat(POSTGRES.isRunning()).isTrue();
        assertThat(REDIS.isRunning()).isTrue();
        assertThat(KAFKA.isRunning()).isTrue();
    }
}
