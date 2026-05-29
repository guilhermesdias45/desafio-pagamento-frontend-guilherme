package com.acaboumony.payment.dto.request;

import com.acaboumony.payment.domain.enums.RefundReason;
import jakarta.validation.constraints.*;

import java.util.UUID;

public record RefundRequest(
    @Min(1) Long amountInCents,
    @NotNull RefundReason reason,
    @NotNull UUID requestedBy,
    @NotNull UUID idempotencyKey
) {}
