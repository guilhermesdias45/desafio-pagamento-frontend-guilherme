package com.acaboumony.fraud.dto.response;

import java.util.List;

/**
 * Immutable response DTO returned from fraud analysis.
 * This data is only shared with the payment-service via the internal API — never with end users.
 */
public record FraudScoreResponse(
        int score,
        String decision,
        List<String> reasons,
        long analysisTimeMs
) {}
