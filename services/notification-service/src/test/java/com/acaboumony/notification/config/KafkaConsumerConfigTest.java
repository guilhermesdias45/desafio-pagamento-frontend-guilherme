package com.acaboumony.notification.config;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.DefaultErrorHandler;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaConsumerConfigTest {

    private final KafkaConsumerConfig config = new KafkaConsumerConfig();

    @Test
    void shouldCreateErrorHandler() {
        DefaultErrorHandler handler = config.kafkaErrorHandler();
        assertThat(handler).isNotNull();
    }

    @Test
    void shouldCreateListenerContainerFactory() {
        var consumerFactory = new DefaultKafkaConsumerFactory<String, Object>(
                Map.of(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092",
                        ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                        ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class));
        DefaultErrorHandler errorHandler = config.kafkaErrorHandler();
        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
                config.kafkaListenerContainerFactory(consumerFactory, errorHandler);
        assertThat(factory).isNotNull();
        assertThat(factory.getConsumerFactory()).isEqualTo(consumerFactory);
    }
}
