package com.acaboumony.payment.event;

import java.time.Instant;
import java.util.UUID;

public record TransactionFailedEvent(
        String transactionId,
        UUID orderId,
        UUID customerId,
        String errorCode,
        Instant timestamp
) {}
