package com.acaboumony.order.event;

import com.acaboumony.order.domain.entity.Order;
import com.acaboumony.order.domain.enums.OrderStatus;
import com.acaboumony.order.repository.OrderRepository;
import com.acaboumony.order.service.OrderCacheService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionEventConsumerTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OrderCacheService orderCacheService;

    private TransactionEventConsumer consumer;

    private UUID orderId;
    private Order pendingOrder;

    @BeforeEach
    void setUp() {
        consumer = new TransactionEventConsumer(orderRepository, orderCacheService);
        orderId = UUID.randomUUID();
        pendingOrder = new Order();
        pendingOrder.setId(orderId);
        pendingOrder.setStatus(OrderStatus.PENDING);
        pendingOrder.setUpdatedAt(Instant.now());
    }

    @Nested
    class TransactionCompleted {

        @Test
        void shouldSetOrderToPaid() {
            var event = new TransactionCompletedEvent("txn_123", orderId, "APPROVED");
            when(orderRepository.findById(orderId)).thenReturn(Optional.of(pendingOrder));
            when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            consumer.consumeTransactionCompleted(event);

            verify(orderRepository).save(pendingOrder);
        }

        @Test
        void shouldSkipWhenOrderNotFound() {
            var event = new TransactionCompletedEvent("txn_123", orderId, "APPROVED");
            when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

            consumer.consumeTransactionCompleted(event);

            verify(orderRepository, never()).save(any());
        }

        @Test
        void shouldSkipWhenOrderNotPending() {
            var paidOrder = new Order();
            paidOrder.setId(orderId);
            paidOrder.setStatus(OrderStatus.PAID);
            var event = new TransactionCompletedEvent("txn_123", orderId, "APPROVED");
            when(orderRepository.findById(orderId)).thenReturn(Optional.of(paidOrder));

            consumer.consumeTransactionCompleted(event);

            verify(orderRepository, never()).save(any());
        }
    }

    @Nested
    class TransactionFailed {

        @Test
        void shouldReturnOrderToPending() {
            pendingOrder.setStatus(OrderStatus.PROCESSING);
            var event = new TransactionFailedEvent("txn_123", orderId, "FAILURE", "CARD_DECLINED");
            when(orderRepository.findById(orderId)).thenReturn(Optional.of(pendingOrder));
            when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            consumer.consumeTransactionFailed(event);

            verify(orderRepository).save(pendingOrder);
        }

        @Test
        void shouldSkipWhenOrderNotFound() {
            var event = new TransactionFailedEvent("txn_123", orderId, "FAILURE", "CARD_DECLINED");
            when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

            consumer.consumeTransactionFailed(event);

            verify(orderRepository, never()).save(any());
        }

        @Test
        void shouldSkipWhenOrderIsPaid() {
            var paidOrder = new Order();
            paidOrder.setId(orderId);
            paidOrder.setStatus(OrderStatus.PAID);
            var event = new TransactionFailedEvent("txn_123", orderId, "FAILURE", "CARD_DECLINED");
            when(orderRepository.findById(orderId)).thenReturn(Optional.of(paidOrder));

            consumer.consumeTransactionFailed(event);

            verify(orderRepository, never()).save(any());
        }
    }

    @Nested
    class TransactionRefunded {

        @Test
        void shouldSetOrderToRefundedWhenFullRefund() {
            pendingOrder.setStatus(OrderStatus.PAID);
            var event = new TransactionRefundedEvent("ref_1", "txn_123", orderId, 1000L, true, "CUSTOMER_REQUEST");
            when(orderRepository.findById(orderId)).thenReturn(Optional.of(pendingOrder));
            when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            consumer.consumeTransactionRefunded(event);

            verify(orderRepository).save(pendingOrder);
        }

        @Test
        void shouldSetOrderToPartiallyRefundedWhenPartial() {
            pendingOrder.setStatus(OrderStatus.PAID);
            var event = new TransactionRefundedEvent("ref_1", "txn_123", orderId, 500L, false, "CUSTOMER_REQUEST");
            when(orderRepository.findById(orderId)).thenReturn(Optional.of(pendingOrder));
            when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            consumer.consumeTransactionRefunded(event);

            verify(orderRepository).save(pendingOrder);
        }

        @Test
        void shouldSkipWhenOrderNotFound() {
            var event = new TransactionRefundedEvent("ref_1", "txn_123", orderId, 1000L, true, "CUSTOMER_REQUEST");
            when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

            consumer.consumeTransactionRefunded(event);

            verify(orderRepository, never()).save(any());
        }
    }
}
