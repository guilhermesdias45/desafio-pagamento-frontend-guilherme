package com.acaboumony.fraud.rules;

import com.acaboumony.fraud.dto.request.FraudAnalysisRequest;
import org.springframework.data.redis.core.StringRedisTemplate;

public class CountryMismatchRule implements FraudRule {

    static final String IP_COUNTRY_PREFIX = "fraud:country:ip:";
    static final String CUSTOMER_COUNTRY_PREFIX = "fraud:country:customer:";

    @Override
    public int evaluate(FraudAnalysisRequest request, StringRedisTemplate redis) {
        String ipCountry = redis.opsForValue().get(IP_COUNTRY_PREFIX + request.ipAddress());
        if (ipCountry == null) return 0;
        String customerCountry = redis.opsForValue().get(CUSTOMER_COUNTRY_PREFIX + request.customerId());
        if (customerCountry == null) return 0;
        return ipCountry.equals(customerCountry) ? 0 : 20;
    }

    @Override
    public String getReason() {
        return "COUNTRY_MISMATCH";
    }

    @Override
    public int getScore() {
        return 20;
    }
}
