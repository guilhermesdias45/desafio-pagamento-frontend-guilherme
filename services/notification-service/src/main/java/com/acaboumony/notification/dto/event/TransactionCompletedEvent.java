package com.acaboumony.notification.dto.event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TransactionCompletedEvent(
        String transactionId,
        Long mpPaymentId,
        UUID orderId,
        UUID customerId,
        UUID merchantId,
        String customerEmail,
        String merchantEmail,
        Long amountInCents,
        String currency,
        String cardBrand,
        String cardLastFour,
        Integer installments,
        List<ItemEvent> items,
        Instant processedAt
) {
    public record ItemEvent(
            String description,
            Integer quantity,
            Long unitPriceInCents
    ) {}
}
