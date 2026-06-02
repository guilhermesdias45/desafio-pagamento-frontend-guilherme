package com.acaboumony.fraud.config;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class KafkaHealthIndicator implements HealthIndicator {

    private final KafkaTemplate<?, ?> kafkaTemplate;

    public KafkaHealthIndicator(KafkaTemplate<?, ?> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public Health health() {
        try {
            kafkaTemplate.metrics();
            return Health.up().withDetail("kafka", "available").build();
        } catch (Exception e) {
            return Health.down().withDetail("kafka", e.getMessage()).build();
        }
    }
}
