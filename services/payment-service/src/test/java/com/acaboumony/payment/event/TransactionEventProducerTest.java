package com.acaboumony.payment.event;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionEventProducerTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Captor
    private ArgumentCaptor<TransactionCompletedEvent> completedCaptor;

    @Captor
    private ArgumentCaptor<TransactionFailedEvent> failedCaptor;

    @Captor
    private ArgumentCaptor<TransactionRefundedEvent> refundedCaptor;

    private TransactionEventProducer producer;

    @BeforeEach
    void setUp() {
        producer = new TransactionEventProducer(kafkaTemplate);
    }

    @Test
    void publishCompleted_sendsToCorrectTopic() {
        when(kafkaTemplate.send(eq("transaction.completed"), eq("txn_001"), any()))
            .thenReturn(CompletableFuture.completedFuture(null));

        var event = new TransactionCompletedEvent(
            "txn_001", 123L, UUID.randomUUID(), UUID.randomUUID(),
            UUID.randomUUID(), "c@t.com", "m@t.com",
            5000L, "BRL", "visa", "1234", 1,
            List.of(new TransactionCompletedEvent.ItemEvent("Item", 1, 1000L)),
            Instant.now(), "APPROVED"
        );
        producer.publishCompleted(event);

        verify(kafkaTemplate).send(eq("transaction.completed"), eq("txn_001"), completedCaptor.capture());
        var captured = completedCaptor.getValue();
        assertEquals("txn_001", captured.transactionId());
        assertEquals(5000L, captured.amountInCents());
        assertNotNull(captured.processedAt());
    }

    @Test
    void publishFailed_sendsToCorrectTopic() {
        when(kafkaTemplate.send(eq("transaction.failed"), eq("txn_001"), any()))
            .thenReturn(CompletableFuture.completedFuture(null));

        var event = new TransactionFailedEvent(
            "txn_001", UUID.randomUUID(), UUID.randomUUID(),
            "c@t.com", 5000L, "CARD_DECLINED",
            Instant.now().toString(), "FAILURE"
        );
        producer.publishFailed(event);

        verify(kafkaTemplate).send(eq("transaction.failed"), eq("txn_001"), failedCaptor.capture());
        assertEquals("CARD_DECLINED", failedCaptor.getValue().reason());
    }

    @Test
    void publishRefunded_sendsToCorrectTopic() {
        when(kafkaTemplate.send(eq("transaction.refunded"), eq("txn_001"), any()))
            .thenReturn(CompletableFuture.completedFuture(null));

        var event = new TransactionRefundedEvent(
            "ref_001", "txn_001", UUID.randomUUID(),
            "c@t.com", 5000L, true, "CUSTOMER_REQUEST",
            7, Instant.now()
        );
        producer.publishRefunded(event);

        verify(kafkaTemplate).send(eq("transaction.refunded"), eq("txn_001"), refundedCaptor.capture());
        assertEquals("ref_001", refundedCaptor.getValue().refundId());
        assertTrue(refundedCaptor.getValue().isFullRefund());
    }
}
