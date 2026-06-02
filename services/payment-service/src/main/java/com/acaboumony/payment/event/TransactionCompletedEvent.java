package com.acaboumony.payment.event;

import java.time.Instant;
import java.util.UUID;

public record TransactionCompletedEvent(
        String transactionId,
        UUID orderId,
        UUID customerId,
        long amountInCents,
        Instant timestamp
) {}
