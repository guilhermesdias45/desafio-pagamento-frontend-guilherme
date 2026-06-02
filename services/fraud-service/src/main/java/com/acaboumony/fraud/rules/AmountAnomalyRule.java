package com.acaboumony.fraud.rules;

import com.acaboumony.fraud.dto.request.FraudAnalysisRequest;
import org.springframework.stereotype.Component;

/**
 * Fires when the transaction amount is more than 5× the customer's 30-day average.
 * Skipped entirely when there is no historical average (new customer with zero average).
 * Adds 25 risk points.
 */
@Component
public class AmountAnomalyRule implements FraudRule {

    @Override
    public int evaluate(FraudAnalysisRequest request, FraudRuleContext context) {
        long avg = context.averageAmountLast30DaysInCents();
        if (avg <= 0) {
            return 0;
        }
        return request.amountInCents() > 5L * avg ? 25 : 0;
    }

    @Override
    public String getRuleId() {
        return "AMOUNT_ANOMALY";
    }
}
