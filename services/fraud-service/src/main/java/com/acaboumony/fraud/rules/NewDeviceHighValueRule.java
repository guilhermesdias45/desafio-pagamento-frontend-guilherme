package com.acaboumony.fraud.rules;

import com.acaboumony.fraud.dto.request.FraudAnalysisRequest;
import org.springframework.stereotype.Component;

/**
 * Fires when a device fingerprint is present (potentially new/unknown device)
 * AND the transaction amount exceeds R$ 500,00 (50 000 cents).
 * Adds 15 risk points.
 */
@Component
public class NewDeviceHighValueRule implements FraudRule {

    private static final long HIGH_VALUE_THRESHOLD_CENTS = 50_000L;

    @Override
    public int evaluate(FraudAnalysisRequest request, FraudRuleContext context) {
        boolean hasFingerprint = request.deviceFingerprint() != null;
        return hasFingerprint && request.amountInCents() > HIGH_VALUE_THRESHOLD_CENTS ? 15 : 0;
    }

    @Override
    public String getRuleId() {
        return "NEW_DEVICE_HIGH_VALUE";
    }
}
