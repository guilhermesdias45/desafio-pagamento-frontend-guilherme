package com.acaboumony.notification.dto.event;

import java.time.Instant;
import java.util.UUID;

public record TransactionRefundedEvent(
        String refundId,
        String transactionId,
        UUID orderId,
        String customerEmail,
        Long amountRefundedInCents,
        Boolean isFullRefund,
        String reason,
        Integer estimatedArrivalDays,
        Instant refundedAt
) {}
