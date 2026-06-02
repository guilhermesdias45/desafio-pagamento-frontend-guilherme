package com.acaboumony.order.dto.response;

import java.util.UUID;

/**
 * Lightweight internal DTO returned to the payment-service via the {@code /internal/orders} endpoint.
 *
 * <p>Exposes only the fields required by payment-service to process the transaction,
 * following the principle of least privilege for inter-service communication.</p>
 */
public record InternalOrderResponse(
        UUID orderId,
        String status,
        long totalInCents,
        UUID merchantId,
        UUID customerId
) {}
