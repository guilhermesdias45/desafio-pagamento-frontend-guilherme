package com.acaboumony.notification.service;

import com.acaboumony.notification.consumer.OrderEventConsumer;
import com.acaboumony.notification.dto.event.OrderCreatedEvent;
import com.acaboumony.notification.service.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OrderEventConsumerTest {

    @Mock
    private EmailService emailService;

    @Captor
    private ArgumentCaptor<String> toCaptor;
    @Captor
    private ArgumentCaptor<String> subjectCaptor;
    @Captor
    private ArgumentCaptor<String> templateCaptor;
    @Captor
    private ArgumentCaptor<Map<String, Object>> variablesCaptor;
    @Captor
    private ArgumentCaptor<String> correlationIdCaptor;

    private OrderEventConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new OrderEventConsumer(emailService);
    }

    @Test
    void shouldProcessOrderCreatedEvent() {
        var orderId = UUID.randomUUID();
        var event = new OrderCreatedEvent(
                orderId, UUID.randomUUID(), UUID.randomUUID(),
                5000L,
                List.of(
                        new OrderCreatedEvent.OrderItemEvent("p1", "Product 1", 2, 1000L, 2000L),
                        new OrderCreatedEvent.OrderItemEvent("p2", "Product 2", 1, 3000L, 3000L)
                ),
                Instant.now()
        );

        consumer.consumeOrderCreated(event);

        verify(emailService).sendEmail(
                toCaptor.capture(),
                subjectCaptor.capture(),
                templateCaptor.capture(),
                variablesCaptor.capture(),
                correlationIdCaptor.capture()
        );

        assertThat(toCaptor.getValue()).isNull();
        assertThat(subjectCaptor.getValue()).contains(orderId.toString());
        assertThat(templateCaptor.getValue()).isEqualTo("order-created");
        assertThat(correlationIdCaptor.getValue()).isEqualTo(orderId.toString());

        var variables = variablesCaptor.getValue();
        assertThat(variables)
                .containsEntry("orderId", orderId.toString())
                .containsEntry("totalInCents", 5000L);
        assertThat((String) variables.get("itemsHtml"))
                .contains("Product 1")
                .contains("Product 2")
                .contains("2")
                .contains("1");
    }

    @Test
    void shouldHandleEmptyItems() {
        var orderId = UUID.randomUUID();
        var event = new OrderCreatedEvent(
                orderId, UUID.randomUUID(), UUID.randomUUID(),
                0L, List.of(), Instant.now()
        );

        consumer.consumeOrderCreated(event);

        verify(emailService).sendEmail(
                null,
                "Pedido confirmado — #" + orderId,
                "order-created",
                Map.of(
                        "orderId", orderId.toString(),
                        "totalInCents", 0L,
                        "itemsHtml", "",
                        "createdAt", event.createdAt().toString()
                ),
                orderId.toString()
        );
    }
}
