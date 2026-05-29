package com.acaboumony.fraud.service;

import com.acaboumony.fraud.dto.request.FraudAnalysisRequest;

public interface ClaudeContextAnalyzer {
    int getContextualAdjustment(FraudAnalysisRequest request, int baseScore);
}
