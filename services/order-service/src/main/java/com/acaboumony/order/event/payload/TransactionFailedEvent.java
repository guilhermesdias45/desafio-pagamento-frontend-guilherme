package com.acaboumony.order.event.payload;

import java.time.Instant;
import java.util.UUID;

/**
 * Kafka event consumed from the payment-service when a transaction fails.
 * Topic: {@code transaction.failed}
 */
public record TransactionFailedEvent(
        String transactionId,
        UUID orderId,
        UUID customerId,
        String errorCode,
        Instant timestamp
) {}
