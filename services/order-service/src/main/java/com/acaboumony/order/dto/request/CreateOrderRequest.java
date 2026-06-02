package com.acaboumony.order.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * Immutable DTO for creating a new order.
 *
 * <p>The {@code idempotencyKey} ensures that duplicate requests (e.g. network retries)
 * return the same response without creating duplicate orders.</p>
 */
public record CreateOrderRequest(
        @NotNull UUID merchantId,
        @NotNull @Size(min = 1) List<@Valid OrderItemRequest> items,
        @NotNull UUID idempotencyKey
) {}
