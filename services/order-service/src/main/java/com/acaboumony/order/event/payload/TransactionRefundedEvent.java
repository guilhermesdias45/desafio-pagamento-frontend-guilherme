package com.acaboumony.order.event.payload;

import java.time.Instant;
import java.util.UUID;

/**
 * Kafka event consumed from the payment-service when a transaction is refunded.
 * Topic: {@code transaction.refunded}
 */
public record TransactionRefundedEvent(
        String transactionId,
        UUID orderId,
        UUID customerId,
        long refundedAmountInCents,
        boolean fullRefund,
        Instant timestamp
) {}
