package com.acaboumony.fraud.rules;

import com.acaboumony.fraud.dto.request.FraudAnalysisRequest;
import org.springframework.data.redis.core.StringRedisTemplate;

public class FirstPurchaseRule implements FraudRule {

    static final String KEY_PREFIX = "fraud:purchase_count:";
    static final long MAX_AMOUNT = 99_999L;

    @Override
    public int evaluate(FraudAnalysisRequest request, StringRedisTemplate redis) {
        String countStr = redis.opsForValue().get(KEY_PREFIX + request.customerId());
        boolean isFirstPurchase = countStr == null;
        return isFirstPurchase && request.amountInCents() >= MAX_AMOUNT ? 20 : 0;
    }

    @Override
    public String getReason() {
        return "FIRST_PURCHASE_MAX_VALUE";
    }

    @Override
    public int getScore() {
        return 20;
    }
}
