package com.acaboumony.fraud.rules;

import com.acaboumony.fraud.dto.request.FraudAnalysisRequest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Instant;

public class VelocityRule implements FraudRule {

    static final String KEY_PREFIX = "fraud:velocity:";

    @Override
    public int evaluate(FraudAnalysisRequest request, StringRedisTemplate redis) {
        String key = KEY_PREFIX + request.customerId();
        long now = Instant.now().toEpochMilli();
        long fiveMinAgo = now - 300_000;
        Long count = redis.opsForZSet().count(key, fiveMinAgo, now);
        return count != null && count >= 3 ? 30 : 0;
    }

    @Override
    public String getReason() {
        return "VELOCITY_EXCEEDED";
    }

    @Override
    public int getScore() {
        return 30;
    }
}
