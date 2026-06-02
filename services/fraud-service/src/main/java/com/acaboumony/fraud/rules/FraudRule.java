package com.acaboumony.fraud.rules;

import com.acaboumony.fraud.dto.request.FraudAnalysisRequest;

/**
 * Strategy interface for individual fraud detection rules.
 *
 * <p>Each rule evaluates a single risk signal and returns the number of risk points
 * to add to the total fraud score. A return value of 0 means the rule did not fire.</p>
 */
public interface FraudRule {

    /**
     * Evaluate the rule against the request and its pre-loaded context.
     *
     * @param request the incoming fraud analysis request
     * @param context Redis-backed contextual data (velocity, blacklists, etc.)
     * @return risk points to add (0 if rule did not fire, positive otherwise)
     */
    int evaluate(FraudAnalysisRequest request, FraudRuleContext context);

    /**
     * Unique human-readable identifier for this rule (e.g. "VELOCITY_EXCEEDED").
     * Included in the reasons list when the rule fires.
     */
    String getRuleId();
}
