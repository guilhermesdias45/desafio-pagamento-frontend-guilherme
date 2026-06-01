package com.acaboumony.order.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class OrderEventProducer {

    private static final Logger log = LoggerFactory.getLogger(OrderEventProducer.class);
    private static final String TOPIC_ORDER_CREATED = "order.created";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public OrderEventProducer(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishOrderCreated(OrderCreatedEvent event) {
        log.info("Publishing order.created event for orderId={}", event.orderId());
        kafkaTemplate.send(TOPIC_ORDER_CREATED, event.orderId().toString(), event);
    }

    private static final String TOPIC_ORDER_CANCELLED = "order.cancelled";

    public void publishOrderCancelled(OrderCancelledEvent event) {
        log.info("Publishing order.cancelled event for orderId={}", event.orderId());
        kafkaTemplate.send(TOPIC_ORDER_CANCELLED, event.orderId().toString(), event);
    }
}
