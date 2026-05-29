package com.acaboumony.fraud.event;

import com.acaboumony.fraud.dto.request.FraudAnalysisRequest;
import com.acaboumony.fraud.dto.response.FraudScore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FraudEventProducerTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Captor
    private ArgumentCaptor<FraudEventProducer.FraudDetectedEvent> eventCaptor;

    private FraudEventProducer producer;
    private final FraudAnalysisRequest request = new FraudAnalysisRequest(
        "txn_001", UUID.randomUUID(), UUID.randomUUID(), 5000L,
        "visa", "192.168.1.1", null, null, null
    );
    private final FraudScore score = new FraudScore(85, "BLOCK", List.of("IP_BLACKLISTED"), 100L);

    @BeforeEach
    void setUp() {
        producer = new FraudEventProducer(kafkaTemplate);
    }

    @Test
    void publishBlockEvent_sendsToFraudDetected() {
        when(kafkaTemplate.send(eq("fraud.detected"), eq("txn_001"), any()))
            .thenReturn(CompletableFuture.completedFuture(null));

        producer.publishBlockEvent(request, score);

        verify(kafkaTemplate).send(eq("fraud.detected"), eq("txn_001"), eventCaptor.capture());
        var event = eventCaptor.getValue();
        assertEquals("txn_001", event.transactionId());
        assertEquals(request.customerId(), event.customerId());
        assertEquals(85, event.score());
        assertEquals("BLOCK", event.decision());
        assertEquals(List.of("IP_BLACKLISTED"), event.reasons());
        assertNotNull(event.detectedAt());
    }

    @Test
    void publishReviewEvent_sendsToFraudReview() {
        when(kafkaTemplate.send(eq("fraud.review"), eq("txn_001"), any()))
            .thenReturn(CompletableFuture.completedFuture(null));

        producer.publishReviewEvent(request, score);

        verify(kafkaTemplate).send(eq("fraud.review"), eq("txn_001"), eventCaptor.capture());
        var event = eventCaptor.getValue();
        assertEquals("txn_001", event.transactionId());
        assertEquals(request.customerId(), event.customerId());
        assertEquals(85, event.score());
        assertEquals("BLOCK", event.decision());
        assertEquals(List.of("IP_BLACKLISTED"), event.reasons());
        assertNotNull(event.detectedAt());
    }

    @Test
    void publishBlockEvent_whenKafkaFails_logsError() {
        when(kafkaTemplate.send(eq("fraud.detected"), eq("txn_001"), any()))
            .thenReturn(CompletableFuture.failedFuture(new RuntimeException("kafka unavailable")));

        producer.publishBlockEvent(request, score);

        verify(kafkaTemplate).send(eq("fraud.detected"), eq("txn_001"), any());
    }

    @Test
    void publishReviewEvent_whenKafkaFails_logsError() {
        when(kafkaTemplate.send(eq("fraud.review"), eq("txn_001"), any()))
            .thenReturn(CompletableFuture.failedFuture(new RuntimeException("kafka unavailable")));

        producer.publishReviewEvent(request, score);

        verify(kafkaTemplate).send(eq("fraud.review"), eq("txn_001"), any());
    }
}
