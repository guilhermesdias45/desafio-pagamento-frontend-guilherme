package com.acaboumony.payment.event;

import java.util.UUID;

public record TransactionFailedEvent(
    String transactionId,
    UUID orderId,
    UUID customerId,
    String customerEmail,
    Long amountInCents,
    String reason,
    String createdAt,
    String status
) {}
