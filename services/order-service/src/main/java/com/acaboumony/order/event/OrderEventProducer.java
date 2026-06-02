package com.acaboumony.order.event;

import com.acaboumony.order.event.payload.OrderCancelledEvent;
import com.acaboumony.order.event.payload.OrderCreatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Publishes order domain events to Kafka topics.
 *
 * <p>Uses orderId as the Kafka message key for partition affinity — all events for the same
 * order land on the same partition, ensuring consumers process them in order.</p>
 */
@Component
public class OrderEventProducer {

    private static final Logger log = LoggerFactory.getLogger(OrderEventProducer.class);

    static final String TOPIC_ORDER_CREATED = "order.created";
    static final String TOPIC_ORDER_CANCELLED = "order.cancelled";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public OrderEventProducer(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishOrderCreated(UUID orderId, UUID customerId, UUID merchantId, long totalInCents) {
        var event = new OrderCreatedEvent(orderId, customerId, merchantId, totalInCents, Instant.now());
        kafkaTemplate.send(TOPIC_ORDER_CREATED, orderId.toString(), event);
        log.info("Published order.created orderId={} customerId={}", orderId, customerId);
    }

    public void publishOrderCancelled(UUID orderId, UUID customerId, String reason) {
        var event = new OrderCancelledEvent(orderId, customerId, reason, Instant.now());
        kafkaTemplate.send(TOPIC_ORDER_CANCELLED, orderId.toString(), event);
        log.info("Published order.cancelled orderId={} reason={}", orderId, reason);
    }
}
