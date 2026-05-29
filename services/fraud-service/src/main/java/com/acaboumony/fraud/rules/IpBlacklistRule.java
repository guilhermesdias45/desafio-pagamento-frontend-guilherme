package com.acaboumony.fraud.rules;

import com.acaboumony.fraud.dto.request.FraudAnalysisRequest;
import org.springframework.data.redis.core.StringRedisTemplate;

public class IpBlacklistRule implements FraudRule {

    static final String KEY_PREFIX = "fraud:ip_blacklist:";

    @Override
    public int evaluate(FraudAnalysisRequest request, StringRedisTemplate redis) {
        String key = KEY_PREFIX + request.ipAddress();
        Boolean blacklisted = redis.opsForSet().isMember(key, request.ipAddress());
        return Boolean.TRUE.equals(blacklisted) ? 40 : 0;
    }

    @Override
    public String getReason() {
        return "IP_BLACKLISTED";
    }

    @Override
    public int getScore() {
        return 40;
    }
}
