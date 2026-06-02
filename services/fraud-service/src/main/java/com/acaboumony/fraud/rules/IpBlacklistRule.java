package com.acaboumony.fraud.rules;

import com.acaboumony.fraud.dto.request.FraudAnalysisRequest;
import org.springframework.stereotype.Component;

/**
 * Fires when the request IP address is present in the Redis blacklist.
 * Adds 40 risk points.
 */
@Component
public class IpBlacklistRule implements FraudRule {

    @Override
    public int evaluate(FraudAnalysisRequest request, FraudRuleContext context) {
        return context.ipInBlacklist() ? 40 : 0;
    }

    @Override
    public String getRuleId() {
        return "IP_BLACKLISTED";
    }
}
