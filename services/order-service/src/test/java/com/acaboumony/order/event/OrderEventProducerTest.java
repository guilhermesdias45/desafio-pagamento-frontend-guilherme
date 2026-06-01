package com.acaboumony.order.event;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderEventProducerTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Captor
    private ArgumentCaptor<OrderCreatedEvent> createdCaptor;
    @Captor
    private ArgumentCaptor<OrderCancelledEvent> cancelledCaptor;

    private OrderEventProducer producer;

    @BeforeEach
    void setUp() {
        producer = new OrderEventProducer(kafkaTemplate);
    }

    @Test
    void shouldPublishOrderCreatedEvent() {
        when(kafkaTemplate.send(any(), any(), any())).thenReturn(CompletableFuture.completedFuture(null));

        var orderId = UUID.randomUUID();
        var event = new OrderCreatedEvent(
                orderId, UUID.randomUUID(), "customer@test.com", UUID.randomUUID(),
                5000L,
                List.of(new OrderCreatedEvent.OrderItemEvent("p1", "Product 1", 2, 1000L, 2000L)),
                Instant.now()
        );

        producer.publishOrderCreated(event);

        verify(kafkaTemplate).send(eq("order.created"), eq(orderId.toString()), createdCaptor.capture());
        assertThat(createdCaptor.getValue().orderId()).isEqualTo(orderId);
        assertThat(createdCaptor.getValue().customerEmail()).isEqualTo("customer@test.com");
    }

    @Test
    void shouldPublishOrderCancelledEvent() {
        when(kafkaTemplate.send(any(), any(), any())).thenReturn(CompletableFuture.completedFuture(null));

        var orderId = UUID.randomUUID();
        var event = new OrderCancelledEvent(
                orderId, UUID.randomUUID(), "customer@test.com", UUID.randomUUID(),
                5000L, "Expired", Instant.now()
        );

        producer.publishOrderCancelled(event);

        verify(kafkaTemplate).send(eq("order.cancelled"), eq(orderId.toString()), cancelledCaptor.capture());
        assertThat(cancelledCaptor.getValue().orderId()).isEqualTo(orderId);
        assertThat(cancelledCaptor.getValue().customerEmail()).isEqualTo("customer@test.com");
    }
}
