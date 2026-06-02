package com.acaboumony.fraud.event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Event published to the {@code fraud.detected} Kafka topic when a transaction
 * is blocked by the fraud detection engine.
 */
public record FraudDetectedEvent(
        String transactionId,
        UUID customerId,
        int score,
        List<String> reasons,
        Instant timestamp
) {}
