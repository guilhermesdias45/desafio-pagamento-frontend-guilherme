package com.acaboumony.fraud.rules;

import com.acaboumony.fraud.dto.request.FraudAnalysisRequest;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneOffset;

/**
 * Fires when the transaction occurs between 02:00 and 05:59 UTC
 * AND the amount exceeds R$ 300,00 (30 000 cents).
 * Adds 10 risk points.
 */
@Component
public class UnusualHourRule implements FraudRule {

    private static final int UNUSUAL_HOUR_START = 2;
    private static final int UNUSUAL_HOUR_END   = 5;
    private static final long MIN_AMOUNT_CENTS  = 30_000L;

    @Override
    public int evaluate(FraudAnalysisRequest request, FraudRuleContext context) {
        int hourUtc = Instant.now().atZone(ZoneOffset.UTC).getHour();
        boolean isUnusualHour = hourUtc >= UNUSUAL_HOUR_START && hourUtc <= UNUSUAL_HOUR_END;
        return isUnusualHour && request.amountInCents() > MIN_AMOUNT_CENTS ? 10 : 0;
    }

    @Override
    public String getRuleId() {
        return "UNUSUAL_HOUR";
    }
}
