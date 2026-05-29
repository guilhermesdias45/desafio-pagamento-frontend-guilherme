package com.acaboumony.fraud.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class KafkaTopicsConfig {

    @Bean
    NewTopic fraudDetectedTopic() {
        return new NewTopic("fraud.detected", 3, (short) 1);
    }

    @Bean
    NewTopic fraudReviewTopic() {
        return new NewTopic("fraud.review", 3, (short) 1);
    }
}
