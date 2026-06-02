package com.acaboumony.order.event.payload;

import java.time.Instant;
import java.util.UUID;

/**
 * Kafka event published when an order is cancelled (user-initiated or expiration).
 * Topic: {@code order.cancelled}
 */
public record OrderCancelledEvent(
        UUID orderId,
        UUID customerId,
        String reason,
        Instant timestamp
) {}
