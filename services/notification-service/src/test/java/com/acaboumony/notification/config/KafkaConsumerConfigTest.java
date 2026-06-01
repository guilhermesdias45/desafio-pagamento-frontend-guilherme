package com.acaboumony.notification.config;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaConsumerConfigTest {

    private final KafkaConsumerConfig config = new KafkaConsumerConfig();

    @Test
    void shouldCreateErrorHandler() {
        var producerFactory = new DefaultKafkaProducerFactory<String, Object>(
                Map.of(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092",
                        ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                        ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class));
        var kafkaTemplate = new KafkaTemplate<>(producerFactory);
        CommonErrorHandler handler = config.kafkaErrorHandler(kafkaTemplate);
        assertThat(handler).isNotNull();
    }

    @Test
    void shouldCreateListenerContainerFactory() {
        var consumerFactory = new DefaultKafkaConsumerFactory<String, Object>(
                Map.of(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092",
                        ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                        ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class));
        var producerFactory = new DefaultKafkaProducerFactory<String, Object>(
                Map.of(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092",
                        ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                        ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class));
        var kafkaTemplate = new KafkaTemplate<>(producerFactory);
        CommonErrorHandler errorHandler = config.kafkaErrorHandler(kafkaTemplate);
        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
                config.kafkaListenerContainerFactory(consumerFactory, errorHandler);
        assertThat(factory).isNotNull();
        assertThat(factory.getConsumerFactory()).isEqualTo(consumerFactory);
    }
}
