package com.acaboumony.order.service;

import com.acaboumony.order.domain.entity.Order;
import com.acaboumony.order.domain.enums.OrderStatus;
import com.acaboumony.order.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderExpirationServiceTest {

    @Mock
    private OrderRepository orderRepository;

    private OrderExpirationService orderExpirationService;

    @BeforeEach
    void setUp() {
        orderExpirationService = new OrderExpirationService(orderRepository);
    }

    @Test
    void shouldExpireStaleOrders() {
        var expiredOrder = new Order();
        expiredOrder.setId(UUID.randomUUID());
        expiredOrder.setStatus(OrderStatus.PENDING);

        when(orderRepository.findByStatusAndExpiresAtBefore(eq(OrderStatus.PENDING), any()))
                .thenReturn(List.of(expiredOrder));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        orderExpirationService.expireStaleOrders();

        verify(orderRepository).save(expiredOrder);
    }

    @Test
    void shouldNotExpireWhenNoStaleOrders() {
        when(orderRepository.findByStatusAndExpiresAtBefore(eq(OrderStatus.PENDING), any()))
                .thenReturn(List.of());

        orderExpirationService.expireStaleOrders();

        verify(orderRepository, never()).save(any());
    }

    @Test
    void shouldHandleEmptyListGracefully() {
        when(orderRepository.findByStatusAndExpiresAtBefore(eq(OrderStatus.PENDING), any()))
                .thenReturn(List.of());

        orderExpirationService.expireStaleOrders();

        verify(orderRepository, never()).save(any());
    }
}
