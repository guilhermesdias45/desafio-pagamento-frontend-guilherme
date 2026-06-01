package com.acaboumony.notification.dto.event;

import java.time.Instant;
import java.util.UUID;

public record OrderCancelledEvent(
        UUID orderId,
        UUID customerId,
        String customerEmail,
        UUID merchantId,
        Long totalInCents,
        String reason,
        Instant cancelledAt
) {}
