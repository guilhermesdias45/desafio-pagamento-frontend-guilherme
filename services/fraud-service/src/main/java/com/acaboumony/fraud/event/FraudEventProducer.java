package com.acaboumony.fraud.event;

import com.acaboumony.fraud.dto.request.FraudAnalysisRequest;
import com.acaboumony.fraud.dto.response.FraudScore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
public class FraudEventProducer {

    private static final Logger log = LoggerFactory.getLogger(FraudEventProducer.class);
    static final String TOPIC_FRAUD_DETECTED = "fraud.detected";
    static final String TOPIC_FRAUD_REVIEW = "fraud.review";

    private final KafkaTemplate<String, Object> kafka;

    public FraudEventProducer(KafkaTemplate<String, Object> kafka) {
        this.kafka = kafka;
    }

    public void publishBlockEvent(FraudAnalysisRequest request, FraudScore score) {
        var event = new FraudDetectedEvent(
            request.transactionId(),
            request.customerId(),
            request.merchantId(),
            score.score(),
            score.decision(),
            score.reasons(),
            Instant.now()
        );
        kafka.send(TOPIC_FRAUD_DETECTED, request.transactionId(), event)
            .whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("Failed to publish fraud.detected for transaction {}", request.transactionId(), ex);
                }
            });
    }

    public void publishReviewEvent(FraudAnalysisRequest request, FraudScore score) {
        var event = new FraudDetectedEvent(
            request.transactionId(),
            request.customerId(),
            request.merchantId(),
            score.score(),
            score.decision(),
            score.reasons(),
            Instant.now()
        );
        kafka.send(TOPIC_FRAUD_REVIEW, request.transactionId(), event)
            .whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("Failed to publish fraud.review for transaction {}", request.transactionId(), ex);
                }
            });
    }

    public record FraudDetectedEvent(
        String transactionId,
        UUID customerId,
        UUID merchantId,
        int score,
        String decision,
        List<String> reasons,
        Instant detectedAt
    ) {}
}
