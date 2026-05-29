package com.acaboumony.fraud.rules;

import com.acaboumony.fraud.dto.request.FraudAnalysisRequest;
import org.springframework.data.redis.core.StringRedisTemplate;

public interface FraudRule {
    int evaluate(FraudAnalysisRequest request, StringRedisTemplate redis);
    String getReason();
    int getScore();
}
