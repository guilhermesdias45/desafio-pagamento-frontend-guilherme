package com.acaboumony.fraud;

import com.acaboumony.fraud.domain.entity.FraudAlert;
import com.acaboumony.fraud.dto.request.FraudAnalysisRequest;
import com.acaboumony.fraud.dto.response.FraudScoreResponse;
import com.acaboumony.fraud.repository.FraudAlertRepository;
import com.acaboumony.fraud.rules.FraudRule;
import com.acaboumony.fraud.rules.FraudRuleContext;
import com.acaboumony.fraud.rules.*;
import com.acaboumony.fraud.service.FraudDetectionService;
import com.acaboumony.fraud.service.FraudEventProducer;
import com.acaboumony.fraud.service.VelocityTrackingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link FraudDetectionService}.
 * All external dependencies are mocked.
 */
@ExtendWith(MockitoExtension.class)
class FraudDetectionServiceTest {

    @Mock VelocityTrackingService velocityTrackingService;
    @Mock FraudAlertRepository fraudAlertRepository;
    @Mock FraudEventProducer fraudEventProducer;

    private FraudDetectionService service;

    private static final UUID CUSTOMER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @BeforeEach
    void setUp() {
        List<FraudRule> rules = List.of(
                new VelocityRule(),
                new AmountAnomalyRule(),
                new IpBlacklistRule(),
                new NewDeviceHighValueRule(),
                new UnusualHourRule(),
                new FirstPurchaseMaxValueRule()
        );
        service = new FraudDetectionService(rules, velocityTrackingService,
                fraudAlertRepository, fraudEventProducer);
    }

    private FraudAnalysisRequest buildRequest(long amountCents) {
        return buildRequest(amountCents, null);
    }

    private FraudAnalysisRequest buildRequest(long amountCents, String fingerprint) {
        return new FraudAnalysisRequest(
                "txn-001", CUSTOMER_ID, amountCents,
                "pm_card_visa", "10.0.0.1",
                fingerprint, null, null
        );
    }

    /** Creates a service with a single custom rule that always returns {@code score} points. */
    private FraudDetectionService serviceWithFixedScoreRule(int score) {
        FraudRule fixedRule = new FraudRule() {
            @Override public int evaluate(FraudAnalysisRequest r, FraudRuleContext c) { return score; }
            @Override public String getRuleId() { return "TEST_RULE_" + score; }
        };
        return new FraudDetectionService(
                List.of(fixedRule), velocityTrackingService, fraudAlertRepository, fraudEventProducer);
    }

    // -----------------------------------------------------------------------
    // Score / decision
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("score_zero_for_new_trusted_client — no rules triggered → APPROVE")
    void score_zero_for_new_trusted_client() {
        when(velocityTrackingService.getVelocityCount(CUSTOMER_ID)).thenReturn(0L);
        when(velocityTrackingService.isIpBlacklisted("10.0.0.1")).thenReturn(false);

        FraudScoreResponse response = service.analyzeTransaction(buildRequest(100L));

        assertThat(response.score()).isEqualTo(0);
        assertThat(response.decision()).isEqualTo("APPROVE");
        assertThat(response.reasons()).isEmpty();
    }

    @Test
    @DisplayName("velocity_rule_adds_30_points — 3+ txns in 5min")
    void velocity_rule_adds_30_points() {
        when(velocityTrackingService.getVelocityCount(CUSTOMER_ID)).thenReturn(3L);
        when(velocityTrackingService.isIpBlacklisted(any())).thenReturn(false);

        FraudScoreResponse response = service.analyzeTransaction(buildRequest(100L));

        assertThat(response.score()).isGreaterThanOrEqualTo(30);
        assertThat(response.reasons()).contains("VELOCITY_EXCEEDED");
    }

    @Test
    @DisplayName("ip_blacklist_adds_40_points — IP in blacklist")
    void ip_blacklist_adds_40_points() {
        when(velocityTrackingService.getVelocityCount(CUSTOMER_ID)).thenReturn(0L);
        when(velocityTrackingService.isIpBlacklisted("10.0.0.1")).thenReturn(true);

        FraudScoreResponse response = service.analyzeTransaction(buildRequest(100L));

        assertThat(response.score()).isGreaterThanOrEqualTo(40);
        assertThat(response.reasons()).contains("IP_BLACKLISTED");
    }

    @Test
    @DisplayName("block_when_score_above_90 — fixed-score rule returns 90 → BLOCK")
    void block_when_score_above_90() {
        // Use a fixed-score rule to guarantee >= 90 deterministically
        FraudDetectionService blockService = serviceWithFixedScoreRule(90);
        when(velocityTrackingService.getVelocityCount(CUSTOMER_ID)).thenReturn(0L);
        when(velocityTrackingService.isIpBlacklisted(any())).thenReturn(false);

        FraudScoreResponse response = blockService.analyzeTransaction(buildRequest(100L));

        assertThat(response.decision()).isEqualTo("BLOCK");
        assertThat(response.score()).isEqualTo(90);
    }

    @Test
    @DisplayName("review_when_score_between_70_and_89 — velocity(30) + blacklist(40) = 70 → REVIEW")
    void review_when_score_between_70_and_89() {
        when(velocityTrackingService.getVelocityCount(CUSTOMER_ID)).thenReturn(3L);
        when(velocityTrackingService.isIpBlacklisted("10.0.0.1")).thenReturn(true);

        FraudScoreResponse response = service.analyzeTransaction(buildRequest(1L));

        assertThat(response.decision()).isEqualTo("REVIEW");
        assertThat(response.score()).isBetween(70, 89);
    }

    @Test
    @DisplayName("approve_when_score_below_70 — only velocity(30) fires → APPROVE")
    void approve_when_score_below_70() {
        when(velocityTrackingService.getVelocityCount(CUSTOMER_ID)).thenReturn(3L);
        when(velocityTrackingService.isIpBlacklisted("10.0.0.1")).thenReturn(false);

        FraudScoreResponse response = service.analyzeTransaction(buildRequest(1L));

        assertThat(response.decision()).isEqualTo("APPROVE");
        assertThat(response.score()).isLessThan(70);
    }

    // -----------------------------------------------------------------------
    // Persistence
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("saves_fraud_alert_on_block — FraudAlertRepository.save called")
    void saves_fraud_alert_on_block() {
        FraudDetectionService blockService = serviceWithFixedScoreRule(90);
        when(velocityTrackingService.getVelocityCount(CUSTOMER_ID)).thenReturn(0L);
        when(velocityTrackingService.isIpBlacklisted(any())).thenReturn(false);

        blockService.analyzeTransaction(buildRequest(100L));

        ArgumentCaptor<FraudAlert> captor = ArgumentCaptor.forClass(FraudAlert.class);
        verify(fraudAlertRepository).save(captor.capture());
        assertThat(captor.getValue().getDecision().name()).isEqualTo("BLOCK");
    }

    @Test
    @DisplayName("saves_fraud_alert_on_review — FraudAlertRepository.save called")
    void saves_fraud_alert_on_review() {
        // velocity(30) + blacklist(40) = 70 → REVIEW
        when(velocityTrackingService.getVelocityCount(CUSTOMER_ID)).thenReturn(3L);
        when(velocityTrackingService.isIpBlacklisted("10.0.0.1")).thenReturn(true);

        service.analyzeTransaction(buildRequest(1L));

        ArgumentCaptor<FraudAlert> captor = ArgumentCaptor.forClass(FraudAlert.class);
        verify(fraudAlertRepository).save(captor.capture());
        assertThat(captor.getValue().getDecision().name()).isEqualTo("REVIEW");
    }

    @Test
    @DisplayName("does_not_save_alert_on_approve — FraudAlertRepository.save NOT called")
    void does_not_save_alert_on_approve() {
        when(velocityTrackingService.getVelocityCount(CUSTOMER_ID)).thenReturn(0L);
        when(velocityTrackingService.isIpBlacklisted("10.0.0.1")).thenReturn(false);

        service.analyzeTransaction(buildRequest(100L));

        verify(fraudAlertRepository, never()).save(any());
    }

    // -----------------------------------------------------------------------
    // Kafka events
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("publishes_kafka_event_on_block — FraudEventProducer called")
    void publishes_kafka_event_on_block() {
        FraudDetectionService blockService = serviceWithFixedScoreRule(90);
        when(velocityTrackingService.getVelocityCount(CUSTOMER_ID)).thenReturn(0L);
        when(velocityTrackingService.isIpBlacklisted(any())).thenReturn(false);

        blockService.analyzeTransaction(buildRequest(100L));

        verify(fraudEventProducer).publishFraudDetected(
                eq("txn-001"), eq(CUSTOMER_ID), eq(90), any());
    }

    @Test
    @DisplayName("does_not_publish_event_on_approve — FraudEventProducer NOT called")
    void does_not_publish_event_on_approve() {
        when(velocityTrackingService.getVelocityCount(CUSTOMER_ID)).thenReturn(0L);
        when(velocityTrackingService.isIpBlacklisted("10.0.0.1")).thenReturn(false);

        service.analyzeTransaction(buildRequest(100L));

        verify(fraudEventProducer, never()).publishFraudDetected(any(), any(), anyInt(), any());
    }

    @Test
    @DisplayName("does_not_publish_event_on_review — FraudEventProducer NOT called")
    void does_not_publish_event_on_review() {
        when(velocityTrackingService.getVelocityCount(CUSTOMER_ID)).thenReturn(3L);
        when(velocityTrackingService.isIpBlacklisted("10.0.0.1")).thenReturn(true);

        service.analyzeTransaction(buildRequest(1L));

        verify(fraudEventProducer, never()).publishFraudDetected(any(), any(), anyInt(), any());
    }

    // -----------------------------------------------------------------------
    // Velocity counter
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("increments_velocity_counter_always — regardless of decision")
    void increments_velocity_counter_always() {
        when(velocityTrackingService.getVelocityCount(CUSTOMER_ID)).thenReturn(0L);
        when(velocityTrackingService.isIpBlacklisted("10.0.0.1")).thenReturn(false);

        service.analyzeTransaction(buildRequest(100L));

        verify(velocityTrackingService).incrementVelocityCounter(CUSTOMER_ID);
    }

    @Test
    @DisplayName("increments_velocity_counter_on_block — counter called even on BLOCK")
    void increments_velocity_counter_on_block() {
        FraudDetectionService blockService = serviceWithFixedScoreRule(90);
        when(velocityTrackingService.getVelocityCount(CUSTOMER_ID)).thenReturn(0L);
        when(velocityTrackingService.isIpBlacklisted(any())).thenReturn(false);

        blockService.analyzeTransaction(buildRequest(100L));

        verify(velocityTrackingService).incrementVelocityCounter(CUSTOMER_ID);
    }

    // -----------------------------------------------------------------------
    // IP Blacklist
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("blacklists_ip_on_block — addToBlacklist called for 24h")
    void blacklists_ip_on_block() {
        FraudDetectionService blockService = serviceWithFixedScoreRule(90);
        when(velocityTrackingService.getVelocityCount(CUSTOMER_ID)).thenReturn(0L);
        when(velocityTrackingService.isIpBlacklisted(any())).thenReturn(false);

        blockService.analyzeTransaction(buildRequest(100L));

        verify(velocityTrackingService).addToBlacklist(eq("10.0.0.1"), any());
    }

    @Test
    @DisplayName("does_not_blacklist_ip_on_approve — addToBlacklist NOT called")
    void does_not_blacklist_ip_on_approve() {
        when(velocityTrackingService.getVelocityCount(CUSTOMER_ID)).thenReturn(0L);
        when(velocityTrackingService.isIpBlacklisted("10.0.0.1")).thenReturn(false);

        service.analyzeTransaction(buildRequest(100L));

        verify(velocityTrackingService, never()).addToBlacklist(any(), any());
    }

    // -----------------------------------------------------------------------
    // Score cap
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("score_capped_at_100 — 3×50 rules firing → score capped at 100 (not 150)")
    void score_capped_at_100() {
        FraudRule rule50a = new FraudRule() {
            @Override public int evaluate(FraudAnalysisRequest r, FraudRuleContext c) { return 50; }
            @Override public String getRuleId() { return "RULE_A"; }
        };
        FraudRule rule50b = new FraudRule() {
            @Override public int evaluate(FraudAnalysisRequest r, FraudRuleContext c) { return 50; }
            @Override public String getRuleId() { return "RULE_B"; }
        };
        FraudRule rule50c = new FraudRule() {
            @Override public int evaluate(FraudAnalysisRequest r, FraudRuleContext c) { return 50; }
            @Override public String getRuleId() { return "RULE_C"; }
        };
        FraudDetectionService capService = new FraudDetectionService(
                List.of(rule50a, rule50b, rule50c),
                velocityTrackingService, fraudAlertRepository, fraudEventProducer);
        when(velocityTrackingService.getVelocityCount(CUSTOMER_ID)).thenReturn(0L);
        when(velocityTrackingService.isIpBlacklisted(any())).thenReturn(false);

        FraudScoreResponse response = capService.analyzeTransaction(buildRequest(100L));

        assertThat(response.score()).isEqualTo(100);
        assertThat(response.decision()).isEqualTo("BLOCK");
    }
}
