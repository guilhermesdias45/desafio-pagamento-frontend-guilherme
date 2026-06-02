package com.acaboumony.order.event.payload;

import java.time.Instant;
import java.util.UUID;

/**
 * Kafka event published when a new order is successfully created.
 * Topic: {@code order.created}
 */
public record OrderCreatedEvent(
        UUID orderId,
        UUID customerId,
        UUID merchantId,
        long totalInCents,
        Instant timestamp
) {}
