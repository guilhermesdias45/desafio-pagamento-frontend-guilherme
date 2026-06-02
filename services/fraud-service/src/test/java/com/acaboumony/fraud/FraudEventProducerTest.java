package com.acaboumony.fraud;

import com.acaboumony.fraud.event.FraudDetectedEvent;
import com.acaboumony.fraud.service.FraudEventProducer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link FraudEventProducer}.
 */
@ExtendWith(MockitoExtension.class)
class FraudEventProducerTest {

    @Mock KafkaTemplate<String, Object> kafkaTemplate;
    @InjectMocks FraudEventProducer fraudEventProducer;

    private static final UUID CUSTOMER_ID = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");

    @Test
    @DisplayName("publishFraudDetected — sends event to fraud.detected topic")
    void publishFraudDetected_sends_to_correct_topic() {
        String transactionId = "txn-123";
        int score = 95;
        List<String> reasons = List.of("VELOCITY_EXCEEDED", "IP_BLACKLISTED");

        fraudEventProducer.publishFraudDetected(transactionId, CUSTOMER_ID, score, reasons);

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate).send(
                eq("fraud.detected"),
                eq(transactionId),
                payloadCaptor.capture()
        );

        FraudDetectedEvent event = (FraudDetectedEvent) payloadCaptor.getValue();
        assertThat(event.transactionId()).isEqualTo(transactionId);
        assertThat(event.customerId()).isEqualTo(CUSTOMER_ID);
        assertThat(event.score()).isEqualTo(score);
        assertThat(event.reasons()).containsExactlyInAnyOrder("VELOCITY_EXCEEDED", "IP_BLACKLISTED");
        assertThat(event.timestamp()).isNotNull();
    }

    @Test
    @DisplayName("publishFraudDetected — topic constant is fraud.detected")
    void topic_constant_is_fraud_detected() {
        assertThat(FraudEventProducer.FRAUD_DETECTED_TOPIC).isEqualTo("fraud.detected");
    }
}
