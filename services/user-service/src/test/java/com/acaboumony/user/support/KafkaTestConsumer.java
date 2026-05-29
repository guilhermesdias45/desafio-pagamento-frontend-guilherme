package com.acaboumony.user.support;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.kafka.test.utils.KafkaTestUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Helper that subscribes to the {@code user-events} topic and returns
 * records received within a timeout window.
 */
public class KafkaTestConsumer implements AutoCloseable {

    private final KafkaConsumer<String, String> consumer;

    public KafkaTestConsumer(String bootstrapServers) {
        Map<String, Object> props = KafkaTestUtils.consumerProps(
                "test-group-" + UUID.randomUUID(), "true", bootstrapServers);
        props.put("key.deserializer", StringDeserializer.class);
        props.put("value.deserializer", StringDeserializer.class);
        props.put("auto.offset.reset", "earliest");
        this.consumer = new KafkaConsumer<>(props);
        this.consumer.subscribe(Collections.singletonList("user-events"));
    }

    /**
     * Polls for records up to {@code timeoutMs} milliseconds.
     */
    public List<ConsumerRecord<String, String>> poll(long timeoutMs) {
        List<ConsumerRecord<String, String>> result = new ArrayList<>();
        ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(timeoutMs));
        records.forEach(result::add);
        return result;
    }

    /**
     * Polls and returns the first record whose value contains {@code eventType}.
     * Retries up to {@code maxAttempts} times with {@code pollMs} timeout each.
     */
    public ConsumerRecord<String, String> pollForEventType(String eventType, int maxAttempts, long pollMs) {
        for (int i = 0; i < maxAttempts; i++) {
            List<ConsumerRecord<String, String>> records = poll(pollMs);
            for (ConsumerRecord<String, String> r : records) {
                if (r.value().contains(eventType)) {
                    return r;
                }
            }
        }
        throw new AssertionError("Expected event type '" + eventType + "' not found after " + maxAttempts + " attempts");
    }

    @Override
    public void close() {
        consumer.close();
    }
}
