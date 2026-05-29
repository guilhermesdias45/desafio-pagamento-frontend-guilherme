package com.acaboumony.notification.dto.event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderCreatedEvent(
        UUID orderId,
        UUID customerId,
        UUID merchantId,
        Long totalInCents,
        List<OrderItemEvent> items,
        Instant createdAt
) {
    public record OrderItemEvent(
            String productId,
            String description,
            Integer quantity,
            Long unitPriceInCents,
            Long subtotalInCents
    ) {}
}
