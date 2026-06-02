package com.acaboumony.order.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Immutable DTO with full order details, including merchant/customer identifiers
 * and the linked transaction ID once payment is processed.
 */
public record OrderDetailResponse(
        UUID orderId,
        UUID customerId,
        UUID merchantId,
        String status,
        long totalInCents,
        List<OrderItemResponse> items,
        String transactionId,
        Instant createdAt,
        Instant updatedAt,
        Instant expiresAt
) {}
