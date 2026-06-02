package com.acaboumony.order.event;

import com.acaboumony.order.event.payload.TransactionCompletedEvent;
import com.acaboumony.order.event.payload.TransactionFailedEvent;
import com.acaboumony.order.event.payload.TransactionRefundedEvent;
import com.acaboumony.order.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TransactionEventConsumerTest {

    @Mock
    private OrderService orderService;

    private TransactionEventConsumer consumer;

    private static final UUID ORDER_ID = UUID.randomUUID();
    private static final UUID CUSTOMER_ID = UUID.randomUUID();
    private static final String TRANSACTION_ID = "TXN-" + UUID.randomUUID();

    @BeforeEach
    void setUp() {
        consumer = new TransactionEventConsumer(orderService);
    }

    @Test
    void completed_event_marks_order_paid() {
        TransactionCompletedEvent event = new TransactionCompletedEvent(
                TRANSACTION_ID, ORDER_ID, CUSTOMER_ID, 1000L, Instant.now());

        consumer.onTransactionCompleted(event);

        verify(orderService).markOrderPaid(ORDER_ID, TRANSACTION_ID);
    }

    @Test
    void failed_event_marks_order_failed() {
        TransactionFailedEvent event = new TransactionFailedEvent(
                TRANSACTION_ID, ORDER_ID, CUSTOMER_ID, "CARD_DECLINED", Instant.now());

        consumer.onTransactionFailed(event);

        verify(orderService).markOrderFailed(ORDER_ID);
    }

    @Test
    void refunded_event_marks_order_refunded() {
        TransactionRefundedEvent event = new TransactionRefundedEvent(
                TRANSACTION_ID, ORDER_ID, CUSTOMER_ID, 1000L, true, Instant.now());

        consumer.onTransactionRefunded(event);

        verify(orderService).markOrderRefunded(ORDER_ID, true);
    }
}
