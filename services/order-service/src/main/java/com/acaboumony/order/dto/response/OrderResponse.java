package com.acaboumony.order.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Immutable DTO returned after creating or retrieving an order (customer-facing view).
 */
public record OrderResponse(
        UUID orderId,
        String status,
        long totalInCents,
        List<OrderItemResponse> items,
        Instant expiresAt,
        Instant createdAt
) {}
