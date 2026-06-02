package com.acaboumony.payment.event;

import java.time.Instant;
import java.util.UUID;

public record TransactionRefundedEvent(
        String transactionId,
        UUID orderId,
        UUID customerId,
        long refundedAmountInCents,
        boolean fullRefund,
        Instant timestamp
) {}
