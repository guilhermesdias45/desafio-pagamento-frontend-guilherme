package com.acaboumony.payment.result;

import com.acaboumony.payment.dto.response.TransactionResponse;

import java.util.UUID;

public sealed interface TransactionResult permits
    TransactionResult.Approved,
    TransactionResult.Failed {

    record Approved(
        String transactionId,
        Long mpPaymentId,
        UUID orderId,
        long processingTimeMs,
        boolean duplicate
    ) implements TransactionResult {}

    record Failed(
        String errorCode,
        String message,
        boolean retryable,
        long processingTimeMs
    ) implements TransactionResult {}

    static TransactionResponse toResponse(TransactionResult result) {
        return switch (result) {
            case Approved a -> new TransactionResponse(
                a.transactionId(), a.mpPaymentId(), a.orderId(),
                "APPROVED", null, null, null, null, null,
                a.processingTimeMs(), null, null
            );
            case Failed f -> new TransactionResponse(
                null, null, null, "FAILURE",
                null, null, null, null, null,
                f.processingTimeMs(), null, null
            );
        };
    }
}
