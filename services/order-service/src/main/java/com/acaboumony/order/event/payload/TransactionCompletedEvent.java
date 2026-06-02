package com.acaboumony.order.event.payload;

import java.time.Instant;
import java.util.UUID;

/**
 * Kafka event consumed from the payment-service when a transaction is successfully completed.
 * Topic: {@code transaction.completed}
 */
public record TransactionCompletedEvent(
        String transactionId,
        UUID orderId,
        UUID customerId,
        long amountInCents,
        Instant timestamp
) {}
