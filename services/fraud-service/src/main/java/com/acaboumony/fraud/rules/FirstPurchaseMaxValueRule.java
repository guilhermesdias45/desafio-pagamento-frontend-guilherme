package com.acaboumony.fraud.rules;

import com.acaboumony.fraud.dto.request.FraudAnalysisRequest;
import org.springframework.stereotype.Component;

/**
 * Fires when this is the customer's first purchase AND the amount is R$ 999,99 or more
 * (≥ 99 999 cents).
 * Adds 20 risk points.
 */
@Component
public class FirstPurchaseMaxValueRule implements FraudRule {

    private static final long MAX_FIRST_PURCHASE_CENTS = 99_999L;

    @Override
    public int evaluate(FraudAnalysisRequest request, FraudRuleContext context) {
        return context.isFirstPurchase() && request.amountInCents() >= MAX_FIRST_PURCHASE_CENTS ? 20 : 0;
    }

    @Override
    public String getRuleId() {
        return "FIRST_PURCHASE_MAX_VALUE";
    }
}
