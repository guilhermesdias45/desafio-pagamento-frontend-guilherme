package com.acaboumony.order.dto.request;

import jakarta.validation.constraints.*;

/**
 * Immutable DTO for a single line item in a create order request.
 *
 * <p>Server-side always recalculates subtotalInCents = quantity × unitPriceInCents.
 * Any client-provided total is ignored.</p>
 */
public record OrderItemRequest(
        @NotBlank String productId,
        @NotBlank @Size(max = 255) String description,
        @NotNull @Min(1) @Max(999) Integer quantity,
        @NotNull @Min(1) @Max(999999) Long unitPriceInCents
) {}
