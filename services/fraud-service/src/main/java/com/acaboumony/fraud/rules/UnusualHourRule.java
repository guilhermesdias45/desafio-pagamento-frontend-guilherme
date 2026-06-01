package com.acaboumony.fraud.rules;

import com.acaboumony.fraud.dto.request.FraudAnalysisRequest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Clock;
import java.time.LocalTime;

public class UnusualHourRule implements FraudRule {

    static final long MIN_AMOUNT = 30_001L;
    static final int UNUSUAL_START = 2;
    static final int UNUSUAL_END = 5;

    private final Clock clock;

    public UnusualHourRule() {
        this.clock = Clock.systemUTC();
    }

    public UnusualHourRule(Clock clock) {
        this.clock = clock;
    }

    @Override
    public int evaluate(FraudAnalysisRequest request, StringRedisTemplate redis) {
        return evaluate(request, redis, clock);
    }

    int evaluate(FraudAnalysisRequest request, StringRedisTemplate redis, Clock clock) {
        LocalTime now = LocalTime.now(clock);
        int hour = now.getHour();
        boolean unusualHour = hour >= UNUSUAL_START && hour < UNUSUAL_END;
        return unusualHour && request.amountInCents() >= MIN_AMOUNT ? 10 : 0;
    }

    @Override
    public String getReason() {
        return "UNUSUAL_HOUR";
    }

    @Override
    public int getScore() {
        return 10;
    }
}
