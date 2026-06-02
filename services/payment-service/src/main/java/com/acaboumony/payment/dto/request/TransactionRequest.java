package com.acaboumony.payment.dto.request;

import jakarta.validation.constraints.*;

import java.util.UUID;

public record TransactionRequest(
        @NotNull @Min(1) @Max(999999) Long amountInCents,
        @NotBlank String currency,
        @NotNull UUID orderId,
        @NotBlank String cardToken,
        @NotBlank String paymentMethodId,
        @Min(1) @Max(12) Integer installments,
        @NotNull UUID idempotencyKey
) {}
