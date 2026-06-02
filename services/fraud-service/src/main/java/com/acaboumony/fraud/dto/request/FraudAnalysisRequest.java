package com.acaboumony.fraud.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

/**
 * Immutable request DTO for fraud analysis submitted by the payment-service
 * via the internal API.
 */
public record FraudAnalysisRequest(
        @NotBlank String transactionId,
        @NotNull UUID customerId,
        @NotNull @Positive Long amountInCents,
        @NotBlank String paymentMethodId,
        @NotBlank String ipAddress,
        String deviceFingerprint,
        Double latitude,
        Double longitude
) {}
