package com.acaboumony.fraud.service;

import com.acaboumony.fraud.dto.request.FraudAnalysisRequest;

public interface ClaudeContextAnalyzer {

    record AdjustmentResult(int adjustment, String reasoning) {}

    int getContextualAdjustment(FraudAnalysisRequest request, int baseScore);

    default AdjustmentResult adjustWithReasoning(FraudAnalysisRequest request, int baseScore) {
        return new AdjustmentResult(getContextualAdjustment(request, baseScore), null);
    }
}
