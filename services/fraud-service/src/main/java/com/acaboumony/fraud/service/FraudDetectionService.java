package com.acaboumony.fraud.service;

import com.acaboumony.fraud.domain.entity.FraudAlert;
import com.acaboumony.fraud.domain.enums.FraudDecision;
import com.acaboumony.fraud.dto.request.FraudAnalysisRequest;
import com.acaboumony.fraud.dto.response.FraudScore;
import com.acaboumony.fraud.event.FraudEventProducer;
import com.acaboumony.fraud.repository.FraudAlertRepository;
import com.acaboumony.fraud.result.FraudResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class FraudDetectionService {

    private static final Logger log = LoggerFactory.getLogger(FraudDetectionService.class);

    static final int FALLBACK_SCORE = 50;

    private final int blockThreshold;
    private final int reviewThreshold;
    private final long analysisTimeoutMs;
    private final long ipBlacklistTtlHours;
    private final int ipBlacklistMinScore;
    private final long velocityTtlMinutes;

    private final RuleEngineService ruleEngine;
    private final ClaudeContextAnalyzer claudeAnalyzer;
    private final FraudAlertRepository alertRepository;
    private final StringRedisTemplate redis;
    private final FraudEventProducer eventProducer;

    public FraudDetectionService(RuleEngineService ruleEngine,
                                 ClaudeContextAnalyzer claudeAnalyzer,
                                 FraudAlertRepository alertRepository,
                                 StringRedisTemplate redis,
                                 FraudEventProducer eventProducer,
                                 @Value("${fraud.block-threshold:90}") int blockThreshold,
                                 @Value("${fraud.review-threshold:70}") int reviewThreshold,
                                 @Value("${fraud.analysis-timeout-ms:250}") long analysisTimeoutMs,
                                 @Value("${fraud.ip-blacklist-ttl-hours:24}") long ipBlacklistTtlHours,
                                 @Value("${fraud.ip-blacklist-min-score:30}") int ipBlacklistMinScore,
                                 @Value("${fraud.velocity-ttl-minutes:5}") long velocityTtlMinutes) {
        this.ruleEngine = ruleEngine;
        this.claudeAnalyzer = claudeAnalyzer;
        this.alertRepository = alertRepository;
        this.redis = redis;
        this.eventProducer = eventProducer;
        this.blockThreshold = blockThreshold;
        this.reviewThreshold = reviewThreshold;
        this.analysisTimeoutMs = analysisTimeoutMs;
        this.ipBlacklistTtlHours = ipBlacklistTtlHours;
        this.ipBlacklistMinScore = ipBlacklistMinScore;
        this.velocityTtlMinutes = velocityTtlMinutes;
    }

    public FraudScore score(FraudAnalysisRequest request) {
        Instant start = Instant.now();

        try {
            recordVelocity(request);
        } catch (Exception e) {
            log.warn("Redis velocity recording failed for transaction {}: {}", request.transactionId(), e.getMessage());
        }

        RuleEngineService.ScoreResult base = ruleEngine.calculateBaseScore(request);
        int finalScore = base.score();
        List<String> reasons = base.reasons();
        int claudeAdjustment = 0;
        String claudeReasoning = null;

        if (finalScore >= reviewThreshold && finalScore < blockThreshold) {
            var adjustmentResult = claudeAnalyzer.adjustWithReasoning(request, finalScore);
            claudeAdjustment = adjustmentResult.adjustment();
            claudeReasoning = adjustmentResult.reasoning();
            finalScore = Math.clamp(finalScore + claudeAdjustment, 0, 100);
        }

        if (reasons.contains("IP_BLACKLISTED")) {
            finalScore = Math.max(finalScore, ipBlacklistMinScore);
        }

        Duration analysisTime = Duration.between(start, Instant.now());

        if (analysisTime.toMillis() > analysisTimeoutMs) {
            log.warn("Analysis timeout for transaction {}: {}ms", request.transactionId(), analysisTime.toMillis());
            return new FraudScore(FALLBACK_SCORE, "APPROVE",
                List.of("TIMEOUT_FALLBACK"), analysisTime.toMillis());
        }

        FraudDecision decision = finalScore >= blockThreshold
            ? FraudDecision.BLOCK
            : finalScore >= reviewThreshold
                ? FraudDecision.REVIEW
                : FraudDecision.APPROVE;

        if (decision == FraudDecision.BLOCK) {
            try {
                autoBlacklistIp(request);
            } catch (Exception e) {
                log.warn("Redis IP blacklist failed for transaction {}: {}", request.transactionId(), e.getMessage());
            }
        }

        if (decision == FraudDecision.BLOCK || decision == FraudDecision.REVIEW) {
            String anonymizedIp = anonymizeIp(request.ipAddress());
            FraudAlert alert = FraudAlert.builder()
                .transactionId(request.transactionId())
                .customerId(request.customerId())
                .score(finalScore)
                .decision(decision)
                .reasons(reasons)
                .claudeAdjustment(claudeAdjustment == 0 ? null : claudeAdjustment)
                .claudeReasoning(claudeReasoning)
                .build();
            alertRepository.save(alert);
            log.info("Fraud alert for transaction {} (ip={}): score={}, decision={}",
                request.transactionId(), anonymizedIp, finalScore, decision);
        }

        var scoreResult = new FraudScore(finalScore, decision.name(), reasons, analysisTime.toMillis());

        switch (decision) {
            case BLOCK -> eventProducer.publishBlockEvent(request, scoreResult);
            case REVIEW -> eventProducer.publishReviewEvent(request, scoreResult);
        }

        FraudResult result = switch (decision) {
            case APPROVE -> new FraudResult.Approved(finalScore, reasons, analysisTime);
            case REVIEW -> new FraudResult.UnderReview(base.score(), finalScore, reasons, analysisTime, claudeAdjustment);
            case BLOCK -> new FraudResult.Blocked(finalScore, reasons, analysisTime);
        };

        return result.toScore();
    }

    private void recordVelocity(FraudAnalysisRequest request) {
        String customerKey = "fraud:velocity:" + request.customerId();
        redis.opsForZSet().add(customerKey, request.transactionId(), (double) System.currentTimeMillis());
        redis.expire(customerKey, velocityTtlMinutes, TimeUnit.MINUTES);

        String merchantKey = "fraud:merchant_velocity:" + request.merchantId();
        redis.opsForZSet().add(merchantKey, request.transactionId(), (double) System.currentTimeMillis());
        redis.expire(merchantKey, velocityTtlMinutes, TimeUnit.MINUTES);
    }

    private void autoBlacklistIp(FraudAnalysisRequest request) {
        String key = "fraud:ip_blacklist:" + request.ipAddress();
        redis.opsForSet().add(key, request.ipAddress());
        redis.expire(key, ipBlacklistTtlHours, TimeUnit.HOURS);
    }

    static String anonymizeIp(String ip) {
        if (ip == null) return null;
        int lastDot = ip.lastIndexOf('.');
        if (lastDot < 0) return ip;
        return ip.substring(0, lastDot + 1) + "0";
    }
}
