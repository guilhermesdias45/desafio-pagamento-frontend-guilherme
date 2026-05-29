package com.acaboumony.order.event;

import java.util.UUID;

public record TransactionCompletedEvent(
        String transactionId,
        UUID orderId,
        String status
) {}
