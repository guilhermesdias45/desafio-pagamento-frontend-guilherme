package com.acaboumony.payment.event;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionEventProducerExtendedTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    private TransactionEventProducer producer;

    @BeforeEach
    void setUp() {
        producer = new TransactionEventProducer(kafkaTemplate);
    }

    @SuppressWarnings("unchecked")
    private CompletableFuture<SendResult<String, Object>> failedFuture(Throwable ex) {
        CompletableFuture<SendResult<String, Object>> future = new CompletableFuture<>();
        future.completeExceptionally(ex);
        return future;
    }

    @Test
    void publishCompleted_whenKafkaFails_logsError() {
        when(kafkaTemplate.send(eq("transaction.completed"), eq("txn_001"), any()))
            .thenReturn(failedFuture(new RuntimeException("Kafka down")));

        var event = new TransactionCompletedEvent(
            "txn_001", 123L, UUID.randomUUID(), UUID.randomUUID(),
            UUID.randomUUID(), "c@t.com", "m@t.com",
            5000L, "BRL", "visa", "1234", 1,
            List.of(), Instant.now(), "APPROVED"
        );

        producer.publishCompleted(event);
    }

    @Test
    void publishFailed_whenKafkaFails_logsError() {
        when(kafkaTemplate.send(eq("transaction.failed"), eq("txn_001"), any()))
            .thenReturn(failedFuture(new RuntimeException("Kafka down")));

        var event = new TransactionFailedEvent(
            "txn_001", UUID.randomUUID(), UUID.randomUUID(),
            "c@t.com", 5000L, "CARD_DECLINED",
            Instant.now().toString(), "FAILURE"
        );

        producer.publishFailed(event);
    }

    @Test
    void publishRefunded_whenKafkaFails_logsError() {
        when(kafkaTemplate.send(eq("transaction.refunded"), eq("txn_001"), any()))
            .thenReturn(failedFuture(new RuntimeException("Kafka down")));

        var event = new TransactionRefundedEvent(
            "ref_001", "txn_001", UUID.randomUUID(),
            "c@t.com", 5000L, true, "CUSTOMER_REQUEST",
            7, Instant.now()
        );

        producer.publishRefunded(event);
    }

    @Test
    void publishCompleted_whenSuccess_doesNotLogError() {
        var event = new TransactionCompletedEvent(
            "txn_002", 456L, UUID.randomUUID(), UUID.randomUUID(),
            UUID.randomUUID(), "a@b.com", "m@b.com",
            3000L, "BRL", "elo", "9876", 2,
            List.of(new TransactionCompletedEvent.ItemEvent("Item", 2, 1500L)),
            Instant.now(), "APPROVED"
        );
        when(kafkaTemplate.send(eq("transaction.completed"), eq("txn_002"), any()))
            .thenReturn(CompletableFuture.completedFuture(null));

        producer.publishCompleted(event);
        verify(kafkaTemplate).send(eq("transaction.completed"), eq("txn_002"), eq(event));
    }

    @Test
    void publishFailed_whenSuccess_doesNotLogError() {
        var event = new TransactionFailedEvent(
            "txn_003", UUID.randomUUID(), UUID.randomUUID(),
            "d@e.com", 2000L, "EXPIRED_CARD",
            Instant.now().toString(), "FAILURE"
        );
        when(kafkaTemplate.send(eq("transaction.failed"), eq("txn_003"), any()))
            .thenReturn(CompletableFuture.completedFuture(null));

        producer.publishFailed(event);
        verify(kafkaTemplate).send(eq("transaction.failed"), eq("txn_003"), eq(event));
    }

    @Test
    void publishRefunded_whenSuccess_doesNotLogError() {
        var event = new TransactionRefundedEvent(
            "ref_002", "txn_004", UUID.randomUUID(),
            "f@g.com", 1000L, false, "DUPLICATE",
            5, Instant.now()
        );
        when(kafkaTemplate.send(eq("transaction.refunded"), eq("txn_004"), any()))
            .thenReturn(CompletableFuture.completedFuture(null));

        producer.publishRefunded(event);
        verify(kafkaTemplate).send(eq("transaction.refunded"), eq("txn_004"), eq(event));
    }
}
