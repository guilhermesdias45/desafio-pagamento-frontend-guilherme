package com.acaboumony.payment.event;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionEventProducerTest {

    @Mock KafkaTemplate<String, Object> kafkaTemplate;

    TransactionEventProducer producer;

    private static final UUID ORDER_ID = UUID.randomUUID();
    private static final UUID CUSTOMER_ID = UUID.randomUUID();
    private static final String TX_ID = "txn_abc123";

    @BeforeEach
    void setUp() {
        producer = new TransactionEventProducer(kafkaTemplate);
    }

    @Test
    void publishes_completed_event_to_correct_topic() {
        producer.publishTransactionCompleted(TX_ID, ORDER_ID, CUSTOMER_ID, 5000L);

        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate).send(eq("transaction.completed"), eq(TX_ID), eventCaptor.capture());

        assertThat(eventCaptor.getValue()).isInstanceOf(TransactionCompletedEvent.class);
        TransactionCompletedEvent event = (TransactionCompletedEvent) eventCaptor.getValue();
        assertThat(event.transactionId()).isEqualTo(TX_ID);
        assertThat(event.orderId()).isEqualTo(ORDER_ID);
        assertThat(event.customerId()).isEqualTo(CUSTOMER_ID);
        assertThat(event.amountInCents()).isEqualTo(5000L);
        assertThat(event.timestamp()).isNotNull();
    }

    @Test
    void publishes_failed_event_to_correct_topic() {
        producer.publishTransactionFailed(TX_ID, ORDER_ID, CUSTOMER_ID, "CARD_DECLINED");

        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate).send(eq("transaction.failed"), eq(TX_ID), eventCaptor.capture());

        assertThat(eventCaptor.getValue()).isInstanceOf(TransactionFailedEvent.class);
        TransactionFailedEvent event = (TransactionFailedEvent) eventCaptor.getValue();
        assertThat(event.errorCode()).isEqualTo("CARD_DECLINED");
    }

    @Test
    void publishes_refunded_event_to_correct_topic() {
        producer.publishTransactionRefunded(TX_ID, ORDER_ID, CUSTOMER_ID, 2500L, false);

        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate).send(eq("transaction.refunded"), eq(TX_ID), eventCaptor.capture());

        assertThat(eventCaptor.getValue()).isInstanceOf(TransactionRefundedEvent.class);
        TransactionRefundedEvent event = (TransactionRefundedEvent) eventCaptor.getValue();
        assertThat(event.refundedAmountInCents()).isEqualTo(2500L);
        assertThat(event.fullRefund()).isFalse();
    }
}
