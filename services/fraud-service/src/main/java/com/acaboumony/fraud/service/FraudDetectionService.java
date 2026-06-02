package com.acaboumony.fraud.service;

import com.acaboumony.fraud.domain.entity.FraudAlert;
import com.acaboumony.fraud.domain.enums.FraudDecision;
import com.acaboumony.fraud.dto.request.FraudAnalysisRequest;
import com.acaboumony.fraud.dto.response.FraudScoreResponse;
import com.acaboumony.fraud.repository.FraudAlertRepository;
import com.acaboumony.fraud.rules.FraudRule;
import com.acaboumony.fraud.rules.FraudRuleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Core fraud detection engine.
 *
 * <p>Evaluates all registered {@link FraudRule} instances against the incoming request,
 * persists BLOCK/REVIEW outcomes, publishes Kafka events for BLOCKed transactions,
 * and increments the velocity counter after every analysis.</p>
 *
 * <p>Score capping: total score is clamped to 100 regardless of how many rules fire.</p>
 */
@Service
public class FraudDetectionService {

    private static final Logger log = LoggerFactory.getLogger(FraudDetectionService.class);

    private final List<FraudRule> rules;
    private final VelocityTrackingService velocityTrackingService;
    private final FraudAlertRepository fraudAlertRepository;
    private final FraudEventProducer eventProducer;

    public FraudDetectionService(List<FraudRule> rules,
                                  VelocityTrackingService velocityTrackingService,
                                  FraudAlertRepository fraudAlertRepository,
                                  FraudEventProducer eventProducer) {
        this.rules = rules;
        this.velocityTrackingService = velocityTrackingService;
        this.fraudAlertRepository = fraudAlertRepository;
        this.eventProducer = eventProducer;
    }

    /**
     * Analyses a transaction for fraud risk deterministically.
     *
     * @param request incoming request from the payment-service internal API
     * @return the fraud score, decision, fired rule IDs, and analysis time
     */
    public FraudScoreResponse analyzeTransaction(FraudAnalysisRequest request) {
        long startTime = System.currentTimeMillis();

        // 1. Build context from Redis
        FraudRuleContext context = buildContext(request);

        // 2. Apply all rules
        int totalScore = 0;
        List<String> reasons = new ArrayList<>();
        for (FraudRule rule : rules) {
            int points = rule.evaluate(request, context);
            if (points > 0) {
                totalScore += points;
                reasons.add(rule.getRuleId());
            }
        }
        totalScore = Math.min(totalScore, 100);

        // 3. Determine decision by threshold
        FraudDecision decision;
        if (totalScore >= 90) {
            decision = FraudDecision.BLOCK;
        } else if (totalScore >= 70) {
            decision = FraudDecision.REVIEW;
        } else {
            decision = FraudDecision.APPROVE;
        }

        long analysisTimeMs = System.currentTimeMillis() - startTime;

        log.info("Fraud analysis: transactionId={} customerId={} score={} decision={} analysisTimeMs={}",
                request.transactionId(), request.customerId(), totalScore, decision, analysisTimeMs);

        // 4. Persist FraudAlert for BLOCK or REVIEW decisions
        if (decision == FraudDecision.BLOCK || decision == FraudDecision.REVIEW) {
            saveFraudAlert(request, totalScore, decision, reasons, analysisTimeMs);
        }

        // 5. Publish Kafka event and auto-blacklist IP on BLOCK
        if (decision == FraudDecision.BLOCK) {
            eventProducer.publishFraudDetected(
                    request.transactionId(), request.customerId(), totalScore, reasons);
            velocityTrackingService.addToBlacklist(request.ipAddress(), Duration.ofHours(24));
        }

        // 6. Increment velocity counter (always, regardless of decision)
        velocityTrackingService.incrementVelocityCounter(request.customerId());

        return new FraudScoreResponse(
                totalScore,
                decision.name(),
                Collections.unmodifiableList(reasons),
                analysisTimeMs);
    }

    // --- Private helpers ---

    private FraudRuleContext buildContext(FraudAnalysisRequest request) {
        long velocityCount = velocityTrackingService.getVelocityCount(request.customerId());
        boolean ipBlacklisted = velocityTrackingService.isIpBlacklisted(request.ipAddress());

        // isFirstPurchase: velocity was 0 before this transaction
        boolean isFirstPurchase = velocityCount == 0;

        // averageAmountLast30DaysInCents: 0 for now (future: load from analytics service)
        // devicesLinkedToCardHash: 0 for now (future: load from device store)
        return new FraudRuleContext(
                velocityCount,
                0L,
                ipBlacklisted,
                0L,
                isFirstPurchase
        );
    }

    private void saveFraudAlert(FraudAnalysisRequest request,
                                 int score,
                                 FraudDecision decision,
                                 List<String> reasons,
                                 long analysisTimeMs) {
        String reasonsCsv = reasons.isEmpty() ? null : String.join(",", reasons);
        FraudAlert alert = new FraudAlert(
                request.transactionId(),
                request.customerId(),
                request.amountInCents(),
                score,
                decision,
                reasonsCsv,
                analysisTimeMs,
                request.ipAddress()
        );
        fraudAlertRepository.save(alert);
        log.info("Persisted FraudAlert: transactionId={} decision={}", request.transactionId(), decision);
    }
}
