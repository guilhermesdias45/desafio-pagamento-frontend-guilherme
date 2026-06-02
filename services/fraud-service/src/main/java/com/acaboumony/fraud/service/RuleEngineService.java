package com.acaboumony.fraud.service;

import com.acaboumony.fraud.dto.request.FraudAnalysisRequest;
import com.acaboumony.fraud.repository.IpBlacklistRepository;
import com.acaboumony.fraud.rules.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class RuleEngineService {

    private final List<FraudRule> rules;
    private final StringRedisTemplate redis;

    public RuleEngineService(StringRedisTemplate redis, IpBlacklistRepository ipBlacklistRepository) {
        this.redis = redis;
        this.rules = List.of(
            new VelocityRule(),
            new AmountAnomalyRule(),
            new IpBlacklistRule(ipBlacklistRepository),
            new CountryMismatchRule(),
            new DeviceFingerprintRule(),
            new CardAbuseRule(),
            new UnusualHourRule(),
            new FirstPurchaseRule(),
            new IpChangeRule(),
            new MerchantPatternRule()
        );
    }

    public ScoreResult calculateBaseScore(FraudAnalysisRequest request) {
        int total = 0;
        List<String> reasons = new ArrayList<>();

        for (FraudRule rule : rules) {
            int points = rule.evaluate(request, redis);
            if (points > 0) {
                total += points;
                reasons.add(rule.getReason());
            }
        }

        return new ScoreResult(Math.min(total, 100), List.copyOf(reasons));
    }

    public record ScoreResult(int score, List<String> reasons) {}
}
