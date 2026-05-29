package com.acaboumony.payment.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TransactionResponse(
    String transactionId,
    Long mpPaymentId,
    UUID orderId,
    String status,
    Long amountInCents,
    String currency,
    String cardBrand,
    String cardLastFour,
    Integer installments,
    Long processingTimeMs,
    Instant createdAt,
    List<RefundSummary> refunds
) {
    public record RefundSummary(
        String refundId,
        Long amountInCents,
        Boolean isFullRefund,
        String reason,
        Instant processedAt
    ) {}
}
