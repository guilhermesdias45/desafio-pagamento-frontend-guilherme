package com.acaboumony.payment.client.order;

import java.util.UUID;

public record InternalOrderResponse(
        UUID orderId,
        String status,
        long totalInCents,
        UUID merchantId,
        UUID customerId
) {}
