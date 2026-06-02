package com.acaboumony.payment.client.mp;

import java.util.UUID;

public record MpPaymentRequest(
        long amountInCents,
        String cardToken,
        String paymentMethodId,
        int installments,
        String payerEmail,
        UUID externalReference,
        UUID idempotencyKey
) {}
