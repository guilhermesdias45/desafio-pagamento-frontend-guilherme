package com.acaboumony.fraud.rules;

import com.acaboumony.fraud.dto.request.FraudAnalysisRequest;
import org.springframework.data.redis.core.StringRedisTemplate;

public class IpChangeRule implements FraudRule {

    static final String IP_LAST_KEY_PREFIX = "fraud:ip_last:";
    static final String IP_TIME_KEY_PREFIX = "fraud:ip_time:";
    static final long ONE_MINUTE_MS = 60_000L;

    @Override
    public int evaluate(FraudAnalysisRequest request, StringRedisTemplate redis) {
        String customerPrefix = "customer:" + request.customerId();
        String lastIp = redis.opsForValue().get(IP_LAST_KEY_PREFIX + customerPrefix);
        if (lastIp == null || lastIp.equals(request.ipAddress())) {
            return 0;
        }
        String timeStr = redis.opsForValue().get(IP_TIME_KEY_PREFIX + customerPrefix);
        if (timeStr == null) return 0;
        long lastChange = Long.parseLong(timeStr);
        return (System.currentTimeMillis() - lastChange) < ONE_MINUTE_MS ? 25 : 0;
    }

    @Override
    public String getReason() {
        return "IP_CHANGE_RAPID";
    }

    @Override
    public int getScore() {
        return 25;
    }
}
