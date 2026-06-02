package com.acaboumony.payment.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TransactionDetailResponse(
        String transactionId,
        Long mpPaymentId,
        String status,
        long amountInCents,
        String currency,
        String cardBrand,
        String cardLastFour,
        String paymentMethodId,
        UUID orderId,
        Instant createdAt,
        Instant updatedAt,
        List<RefundSummary> refunds,
        Long processingTimeMs
) {}
