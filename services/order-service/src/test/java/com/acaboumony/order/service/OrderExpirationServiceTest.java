package com.acaboumony.order.service;

import com.acaboumony.order.domain.entity.Order;
import com.acaboumony.order.domain.enums.OrderStatus;
import com.acaboumony.order.event.OrderEventProducer;
import com.acaboumony.order.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderExpirationServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderEventProducer eventProducer;

    private OrderExpirationService expirationService;

    @BeforeEach
    void setUp() {
        expirationService = new OrderExpirationService(orderRepository, eventProducer);
    }

    @Test
    void cancels_expired_pending_orders() {
        Order expiredOrder = buildOrder(OrderStatus.PENDING);
        when(orderRepository.findExpiredPendingOrders(any(Instant.class)))
                .thenReturn(List.of(expiredOrder));
        when(orderRepository.save(any(Order.class))).thenReturn(expiredOrder);

        expirationService.cancelExpiredOrders();

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void does_not_cancel_non_pending_orders() {
        // No expired orders returned
        when(orderRepository.findExpiredPendingOrders(any(Instant.class)))
                .thenReturn(List.of());

        expirationService.cancelExpiredOrders();

        verify(orderRepository, never()).save(any());
        verifyNoInteractions(eventProducer);
    }

    @Test
    void publishes_kafka_event_on_expiration() {
        Order expiredOrder = buildOrder(OrderStatus.PENDING);
        when(orderRepository.findExpiredPendingOrders(any(Instant.class)))
                .thenReturn(List.of(expiredOrder));
        when(orderRepository.save(any(Order.class))).thenReturn(expiredOrder);

        expirationService.cancelExpiredOrders();

        verify(eventProducer).publishOrderCancelled(
                eq(expiredOrder.getId()),
                eq(expiredOrder.getCustomerId()),
                eq("EXPIRATION")
        );
    }

    private Order buildOrder(OrderStatus status) {
        Order order = new Order();
        order.setId(UUID.randomUUID());
        order.setCustomerId(UUID.randomUUID());
        order.setMerchantId(UUID.randomUUID());
        order.setStatus(status);
        order.setTotalInCents(1000L);
        order.setIdempotencyKey(UUID.randomUUID());
        order.setExpiresAt(Instant.now().minusSeconds(60));
        order.setCreatedAt(Instant.now().minusSeconds(960));
        order.setUpdatedAt(Instant.now().minusSeconds(960));
        order.setItems(new ArrayList<>());
        return order;
    }
}
