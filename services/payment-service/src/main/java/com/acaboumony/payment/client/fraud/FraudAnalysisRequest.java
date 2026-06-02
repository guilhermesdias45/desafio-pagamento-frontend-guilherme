package com.acaboumony.payment.client.fraud;

import java.util.UUID;

public record FraudAnalysisRequest(
        String transactionId,
        UUID customerId,
        long amountInCents,
        String paymentMethodId,
        String ipAddress
) {}
