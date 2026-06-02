package com.acaboumony.fraud.rules;

import com.acaboumony.fraud.dto.request.FraudAnalysisRequest;
import org.springframework.stereotype.Component;

/**
 * Fires when the customer has made 3 or more transactions in the last 5 minutes.
 * Adds 30 risk points.
 */
@Component
public class VelocityRule implements FraudRule {

    @Override
    public int evaluate(FraudAnalysisRequest request, FraudRuleContext context) {
        return context.transactionsInLast5MinForCustomer() >= 3 ? 30 : 0;
    }

    @Override
    public String getRuleId() {
        return "VELOCITY_EXCEEDED";
    }
}
