package com.acaboumony.payment.result;

import java.util.UUID;

public sealed interface TransactionResult
        permits TransactionResult.Success, TransactionResult.Failure {

    record Success(
            String transactionId,
            Long mpPaymentId,
            UUID orderId,
            long processingTimeMs
    ) implements TransactionResult {}

    record Failure(
            String errorCode,
            String message,
            boolean retryable,
            long processingTimeMs
    ) implements TransactionResult {}
}
