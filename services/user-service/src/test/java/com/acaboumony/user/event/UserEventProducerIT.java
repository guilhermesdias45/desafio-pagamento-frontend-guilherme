package com.acaboumony.user.event;

import com.acaboumony.user.domain.enums.UserRole;
import com.acaboumony.user.support.BaseIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UserEventProducerIT extends BaseIntegrationTest {

    @Autowired
    private UserEventProducer producer;

    @Autowired
    private ObjectMapper objectMapper;

    private KafkaConsumer<String, String> consumer;

    @BeforeEach
    void setUpConsumer() {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumer = new KafkaConsumer<>(props);
        consumer.subscribe(List.of(UserEventProducer.TOPIC));
        // Drain any pre-existing records
        consumer.poll(Duration.ofMillis(200));
    }

    @AfterEach
    void closeConsumer() {
        if (consumer != null) consumer.close();
    }

    private List<ConsumerRecord<String, String>> pollRecords(int expected) throws InterruptedException {
        List<ConsumerRecord<String, String>> records = new ArrayList<>();
        long deadline = System.currentTimeMillis() + 10_000;
        while (records.size() < expected && System.currentTimeMillis() < deadline) {
            consumer.poll(Duration.ofMillis(300)).forEach(records::add);
        }
        return records;
    }

    @Test
    void deve_publicar_user_registered_no_topico_user_events_quando_publishUserRegistered() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID merchantId = UUID.randomUUID();

        producer.publishUserRegistered(userId, "reg@test.com", UserRole.MERCHANT_OWNER, merchantId);

        List<ConsumerRecord<String, String>> records = pollRecords(1);
        assertThat(records).hasSize(1);
        Map<?, ?> payload = objectMapper.readValue(records.get(0).value(), Map.class);
        assertThat(payload.get("eventType")).isEqualTo("user.registered");
        assertThat(payload.get("userId")).isEqualTo(userId.toString());
        assertThat(payload.get("email")).isEqualTo("reg@test.com");
        assertThat(payload.get("merchantId")).isEqualTo(merchantId.toString());
    }

    @Test
    void deve_publicar_user_login_blocked_com_unlockAt_quando_publishLoginBlocked() throws Exception {
        UUID userId = UUID.randomUUID();
        Instant unlockAt = Instant.now().plusSeconds(1800);

        producer.publishLoginBlocked(userId, "blocked@test.com", unlockAt);

        List<ConsumerRecord<String, String>> records = pollRecords(1);
        assertThat(records).hasSize(1);
        Map<?, ?> payload = objectMapper.readValue(records.get(0).value(), Map.class);
        assertThat(payload.get("eventType")).isEqualTo("user.login.blocked");
        assertThat(payload.get("unlockAt")).isNotNull();
    }

    @Test
    void deve_publicar_user_login_success_quando_publishLoginSuccess() throws Exception {
        UUID userId = UUID.randomUUID();

        producer.publishLoginSuccess(userId, "success@test.com", "device-fp-abc");

        List<ConsumerRecord<String, String>> records = pollRecords(1);
        assertThat(records).hasSize(1);
        Map<?, ?> payload = objectMapper.readValue(records.get(0).value(), Map.class);
        assertThat(payload.get("eventType")).isEqualTo("user.login.success");
    }

    @Test
    void deve_publicar_user_2fa_enabled_quando_publishTwoFactorEnabled() throws Exception {
        UUID userId = UUID.randomUUID();

        producer.publishTwoFactorEnabled(userId);

        List<ConsumerRecord<String, String>> records = pollRecords(1);
        assertThat(records).hasSize(1);
        Map<?, ?> payload = objectMapper.readValue(records.get(0).value(), Map.class);
        assertThat(payload.get("eventType")).isEqualTo("user.2fa.enabled");
    }

    @Test
    void deve_usar_userId_como_key_da_mensagem_quando_publish_qualquer_tipo() throws Exception {
        UUID userId = UUID.randomUUID();

        producer.publishUserRegistered(userId, "key@test.com", UserRole.CUSTOMER, null);

        List<ConsumerRecord<String, String>> records = pollRecords(1);
        assertThat(records).hasSize(1);
        assertThat(records.get(0).key()).isEqualTo(userId.toString());
    }
}
