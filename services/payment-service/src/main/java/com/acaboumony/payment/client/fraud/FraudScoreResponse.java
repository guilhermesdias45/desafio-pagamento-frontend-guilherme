package com.acaboumony.payment.client.fraud;

import java.util.List;

public record FraudScoreResponse(
        int score,
        String decision,
        List<String> reasons,
        long analysisTimeMs
) {}
