package com.acaboumony.fraud.rules;

import com.acaboumony.fraud.dto.request.FraudAnalysisRequest;
import org.springframework.data.redis.core.StringRedisTemplate;

public class CardAbuseRule implements FraudRule {

    static final String KEY_PREFIX = "fraud:card:";

    @Override
    public int evaluate(FraudAnalysisRequest request, StringRedisTemplate redis) {
        String countStr = redis.opsForValue().get(KEY_PREFIX + request.paymentMethodId());
        if (countStr == null) return 0;
        long count = Long.parseLong(countStr);
        return count >= 3 ? 35 : 0;
    }

    @Override
    public String getReason() {
        return "CARD_ABUSE";
    }

    @Override
    public int getScore() {
        return 35;
    }
}
