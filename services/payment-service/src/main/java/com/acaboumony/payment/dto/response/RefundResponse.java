package com.acaboumony.payment.dto.response;

import java.time.Instant;

public record RefundResponse(
    String refundId,
    String transactionId,
    Long amountInCents,
    Boolean isFullRefund,
    String reason,
    String status,
    Instant processedAt
) {}
