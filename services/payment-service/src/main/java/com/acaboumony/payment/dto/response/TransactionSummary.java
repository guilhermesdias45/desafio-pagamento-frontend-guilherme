package com.acaboumony.payment.dto.response;

import com.acaboumony.payment.domain.entity.Transaction;
import java.time.Instant;

public record TransactionSummary(
    String transactionId,
    Long mpPaymentId,
    String status,
    Long amountInCents,
    String currency,
    String cardBrand,
    String cardLastFour,
    Instant createdAt,
    Long processingTimeMs
) {
    public static TransactionSummary from(Transaction tx) {
        return new TransactionSummary(
            tx.getTransactionId(),
            tx.getMpPaymentId(),
            tx.getStatus() != null ? tx.getStatus().name() : null,
            tx.getAmountInCents(),
            tx.getCurrency(),
            tx.getCardBrand(),
            tx.getCardLastFour(),
            tx.getCreatedAt(),
            tx.getProcessingTimeMs()
        );
    }
}
