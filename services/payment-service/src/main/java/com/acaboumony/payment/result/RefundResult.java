package com.acaboumony.payment.result;

public sealed interface RefundResult
        permits RefundResult.Success, RefundResult.Failure {

    record Success(
            String refundId,
            long amountInCents,
            String status
    ) implements RefundResult {}

    record Failure(
            String errorCode,
            String message,
            boolean retryable
    ) implements RefundResult {}
}
