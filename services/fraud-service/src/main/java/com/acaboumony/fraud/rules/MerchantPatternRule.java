package com.acaboumony.fraud.rules;

import com.acaboumony.fraud.dto.request.FraudAnalysisRequest;
import org.springframework.data.redis.core.StringRedisTemplate;

public class MerchantPatternRule implements FraudRule {

    static final String KEY_PREFIX = "fraud:merchant_velocity:";

    @Override
    public int evaluate(FraudAnalysisRequest request, StringRedisTemplate redis) {
        String key = KEY_PREFIX + request.merchantId();
        long now = System.currentTimeMillis();
        long fiveMinAgo = now - 300_000;
        Long count = redis.opsForZSet().count(key, fiveMinAgo, now);
        if (count == null) return 0;
        return count >= 5 ? 20 : count >= 3 ? 10 : 0;
    }

    @Override
    public String getReason() {
        return "MERCHANT_PATTERN";
    }

    @Override
    public int getScore() {
        return 20;
    }
}
