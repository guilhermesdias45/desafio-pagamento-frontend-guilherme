package com.acaboumony.order.dto.response;

/**
 * Immutable DTO representing a single line item in an order response.
 */
public record OrderItemResponse(
        String productId,
        String description,
        int quantity,
        long unitPriceInCents,
        long subtotalInCents
) {}
