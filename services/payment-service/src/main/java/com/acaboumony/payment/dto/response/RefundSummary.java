package com.acaboumony.payment.dto.response;

import java.time.Instant;
import java.util.UUID;

public record RefundSummary(
        UUID refundId,
        long amountInCents,
        String reason,
        String status,
        Instant createdAt
) {}
