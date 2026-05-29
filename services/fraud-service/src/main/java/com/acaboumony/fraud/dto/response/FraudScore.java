package com.acaboumony.fraud.dto.response;

import java.util.List;

public record FraudScore(
    Integer score,
    String decision,
    List<String> reasons,
    Long analysisTimeMs
) {}
