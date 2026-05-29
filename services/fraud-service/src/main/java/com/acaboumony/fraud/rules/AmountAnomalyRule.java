package com.acaboumony.fraud.rules;

import com.acaboumony.fraud.dto.request.FraudAnalysisRequest;
import org.springframework.data.redis.core.StringRedisTemplate;

public class AmountAnomalyRule implements FraudRule {

    static final String KEY_PREFIX = "fraud:avg:";

    @Override
    public int evaluate(FraudAnalysisRequest request, StringRedisTemplate redis) {
        String avgStr = redis.opsForValue().get(KEY_PREFIX + request.customerId());
        if (avgStr == null) return 0;
        long avg = Long.parseLong(avgStr);
        return request.amountInCents() >= avg * 5 ? 25 : 0;
    }

    @Override
    public String getReason() {
        return "AMOUNT_ANOMALY";
    }

    @Override
    public int getScore() {
        return 25;
    }
}
