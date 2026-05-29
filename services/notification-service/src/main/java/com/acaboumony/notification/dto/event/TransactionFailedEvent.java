package com.acaboumony.notification.dto.event;

import java.util.UUID;

public record TransactionFailedEvent(
        String transactionId,
        UUID orderId,
        UUID customerId,
        String customerEmail,
        Long amountInCents,
        String reason,
        String createdAt
) {}
