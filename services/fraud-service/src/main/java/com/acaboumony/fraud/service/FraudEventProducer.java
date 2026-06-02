package com.acaboumony.fraud.service;

import com.acaboumony.fraud.event.FraudDetectedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Publishes fraud-related events to Kafka topics.
 */
@Service
public class FraudEventProducer {

    private static final Logger log = LoggerFactory.getLogger(FraudEventProducer.class);

    public static final String FRAUD_DETECTED_TOPIC = "fraud.detected";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public FraudEventProducer(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Publishes a {@link FraudDetectedEvent} when a transaction is blocked.
     *
     * @param transactionId the blocked transaction identifier
     * @param customerId    the customer who attempted the transaction
     * @param score         the computed fraud score (0–100)
     * @param reasons       list of rule IDs that fired
     */
    public void publishFraudDetected(String transactionId,
                                     UUID customerId,
                                     int score,
                                     List<String> reasons) {
        FraudDetectedEvent event = new FraudDetectedEvent(
                transactionId, customerId, score, List.copyOf(reasons), Instant.now());
        kafkaTemplate.send(FRAUD_DETECTED_TOPIC, transactionId, event);
        log.info("Published fraud.detected event: transactionId={} customerId={} score={}",
                transactionId, customerId, score);
    }
}
