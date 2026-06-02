package com.acaboumony.payment.dto.response;

import java.util.UUID;

public record TransactionResponse(
        String transactionId,
        Long mpPaymentId,
        UUID orderId,
        String status,
        Long processingTimeMs
) {}
