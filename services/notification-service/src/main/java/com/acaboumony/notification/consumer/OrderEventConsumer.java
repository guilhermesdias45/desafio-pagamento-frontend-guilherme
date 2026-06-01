package com.acaboumony.notification.consumer;

import com.acaboumony.notification.dto.event.OrderCancelledEvent;
import com.acaboumony.notification.dto.event.OrderCreatedEvent;
import com.acaboumony.notification.service.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class OrderEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderEventConsumer.class);

    private final EmailService emailService;

    public OrderEventConsumer(EmailService emailService) {
        this.emailService = emailService;
    }

    @KafkaListener(topics = "order.created", groupId = "notification-service-group")
    public void consumeOrderCreated(OrderCreatedEvent event) {
        log.info("Received order.created event for orderId={}", event.orderId());

        var itemsHtml = event.items().stream()
                .map(i -> String.format("%s x%d — R$ %.2f",
                        i.description(), i.quantity(), i.subtotalInCents() / 100.0))
                .reduce((a, b) -> a + "<br/>" + b)
                .orElse("");

        var variables = Map.<String, Object>of(
                "orderId", event.orderId().toString(),
                "totalInCents", event.totalInCents(),
                "itemsHtml", itemsHtml,
                "createdAt", event.createdAt().toString()
        );
        emailService.sendEmail(
                event.customerEmail(),
                "Pedido confirmado — #" + event.orderId(),
                "order-created",
                variables,
                event.orderId().toString()
        );
    }

    @KafkaListener(topics = "order.cancelled", groupId = "notification-service-group")
    public void consumeOrderCancelled(OrderCancelledEvent event) {
        log.info("Received order.cancelled event for orderId={}", event.orderId());

        var variables = Map.<String, Object>of(
                "orderId", event.orderId().toString(),
                "reason", event.reason()
        );
        emailService.sendEmail(
                event.customerEmail(),
                "Pedido cancelado — #" + event.orderId(),
                "order-created",
                variables,
                event.orderId().toString()
        );
    }
}
