package com.acaboumony.payment.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record RefundRequest(
        Long amountInCents,
        @NotBlank String reason,
        @NotNull UUID idempotencyKey
) {}
