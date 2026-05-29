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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class FraudDetectionService {

    private static final Logger log = LoggerFactory.getLogger(FraudDetectionService.class);

    static final int BLOCK_THRESHOLD = 90;
    static final int REVIEW_THRESHOLD = 70;
    static final long ANALYSIS_TIMEOUT_MS = 250L;
    static final int FALLBACK_SCORE = 50;
    static final long IP_BLACKLIST_TTL_HOURS = 24;
    static final int IP_BLACKLIST_MIN_SCORE = 30;
    static final long VELOCITY_TTL_MINUTES = 5;

    private final RuleEngineService ruleEngine;
    private final ClaudeContextAnalyzer claudeAnalyzer;
    private final FraudAlertRepository alertRepository;
    private final StringRedisTemplate redis;
    private final FraudEventProducer eventProducer;

    public FraudDetectionService(RuleEngineService ruleEngine,
                                 ClaudeContextAnalyzer claudeAnalyzer,
                                 FraudAlertRepository alertRepository,
                                 StringRedisTemplate redis,
                                 FraudEventProducer eventProducer) {
        this.ruleEngine = ruleEngine;
        this.claudeAnalyzer = claudeAnalyzer;
        this.alertRepository = alertRepository;
        this.redis = redis;
        this.eventProducer = eventProducer;
    }

    public FraudScore score(FraudAnalysisRequest request) {
        Instant start = Instant.now();

        recordVelocity(request);

        RuleEngineService.ScoreResult base = ruleEngine.calculateBaseScore(request);
        int finalScore = base.score();
        List<String> reasons = base.reasons();
        int claudeAdjustment = 0;

        if (finalScore >= REVIEW_THRESHOLD && finalScore < BLOCK_THRESHOLD) {
            claudeAdjustment = claudeAnalyzer.getContextualAdjustment(request, finalScore);
            finalScore = Math.clamp(finalScore + claudeAdjustment, 0, 100);
        }

        if (reasons.contains("IP_BLACKLISTED")) {
            finalScore = Math.max(finalScore, IP_BLACKLIST_MIN_SCORE);
        }

        FraudDecision decision = finalScore >= BLOCK_THRESHOLD
            ? FraudDecision.BLOCK
            : finalScore >= REVIEW_THRESHOLD
                ? FraudDecision.REVIEW
                : FraudDecision.APPROVE;

        Duration analysisTime = Duration.between(start, Instant.now());

        if (analysisTime.toMillis() > ANALYSIS_TIMEOUT_MS) {
            log.warn("Analysis timeout for transaction {}: {}ms", request.transactionId(), analysisTime.toMillis());
            return new FraudScore(FALLBACK_SCORE, "APPROVE",
                List.of("TIMEOUT_FALLBACK"), analysisTime.toMillis());
        }

        if (decision == FraudDecision.BLOCK) {
            autoBlacklistIp(request);
        }

        if (decision == FraudDecision.BLOCK || decision == FraudDecision.REVIEW) {
            FraudAlert alert = FraudAlert.builder()
                .transactionId(request.transactionId())
                .customerId(request.customerId())
                .score(finalScore)
                .decision(decision)
                .reasons(reasons)
                .claudeAdjustment(claudeAdjustment == 0 ? null : claudeAdjustment)
                .build();
            alertRepository.save(alert);
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
        String key = "fraud:velocity:" + request.customerId();
        redis.opsForZSet().add(key, request.transactionId(), (double) System.currentTimeMillis());
        redis.expire(key, VELOCITY_TTL_MINUTES, TimeUnit.MINUTES);
    }

    private void autoBlacklistIp(FraudAnalysisRequest request) {
        String key = "fraud:ip_blacklist:" + request.ipAddress();
        redis.opsForSet().add(key, request.ipAddress());
        redis.expire(key, IP_BLACKLIST_TTL_HOURS, TimeUnit.HOURS);
    }
}
