package com.acaboumony.fraud.rules;

import com.acaboumony.fraud.dto.request.FraudAnalysisRequest;
import org.springframework.data.redis.core.StringRedisTemplate;

public class DeviceFingerprintRule implements FraudRule {

    static final String KEY_PREFIX = "fraud:devices:";
    static final long HIGH_VALUE_THRESHOLD = 50_000L;

    @Override
    public int evaluate(FraudAnalysisRequest request, StringRedisTemplate redis) {
        if (request.deviceFingerprint() == null || request.deviceFingerprint().isBlank()) {
            return 0;
        }
        if (request.amountInCents() <= HIGH_VALUE_THRESHOLD) {
            return 0;
        }
        Boolean known = redis.opsForSet().isMember(KEY_PREFIX + request.customerId(), request.deviceFingerprint());
        return Boolean.FALSE.equals(known) ? 15 : 0;
    }

    @Override
    public String getReason() {
        return "NEW_DEVICE_HIGH_VALUE";
    }

    @Override
    public int getScore() {
        return 15;
    }
}
