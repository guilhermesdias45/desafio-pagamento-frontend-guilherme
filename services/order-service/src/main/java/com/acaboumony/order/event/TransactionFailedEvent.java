package com.acaboumony.order.event;

import java.util.UUID;

public record TransactionFailedEvent(
        String transactionId,
        UUID orderId,
        String status,
        String reason
) {}
