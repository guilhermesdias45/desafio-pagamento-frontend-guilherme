package com.acaboumony.payment.dto.request;

import jakarta.validation.constraints.*;

import java.util.UUID;

public record TransactionRequest(
    @NotNull @Min(1) @Max(999999) Long amountInCents,
    @NotBlank @Pattern(regexp = "^BRL$") String currency,
    @NotNull UUID customerId,
    @NotNull UUID orderId,
    @NotBlank @Pattern(regexp = "^[a-fA-F0-9]{32}$") String cardToken,
    @NotBlank String paymentMethodId,
    @Min(1) @Max(12) Integer installments,
    @NotNull UUID idempotencyKey
) {
    public TransactionRequest {
        if (installments == null) installments = 1;
    }
}
