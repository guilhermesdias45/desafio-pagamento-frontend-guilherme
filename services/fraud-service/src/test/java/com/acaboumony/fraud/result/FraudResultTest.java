package com.acaboumony.fraud.result;

import com.acaboumony.fraud.domain.enums.FraudDecision;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FraudResultTest {

    @Test
    void approved_hasCorrectDecisionAndScore() {
        var approved = new FraudResult.Approved(10, List.of("LOW_RISK"), Duration.ofMillis(50));
        assertEquals(FraudDecision.APPROVE, approved.decision());
        assertEquals("APPROVE", approved.toScore().decision());
        assertEquals(10, approved.toScore().score());
        assertEquals(List.of("LOW_RISK"), approved.reasons());
        assertEquals(50L, approved.toScore().analysisTimeMs());
    }

    @Test
    void underReview_hasCorrectDecisionAndScore() {
        var review = new FraudResult.UnderReview(50, List.of("AMOUNT_ANOMALY"), Duration.ofMillis(80));
        assertEquals(FraudDecision.REVIEW, review.decision());
        assertEquals("REVIEW", review.toScore().decision());
        assertEquals(50, review.toScore().score());
        assertEquals(List.of("AMOUNT_ANOMALY"), review.reasons());
        assertEquals(80L, review.toScore().analysisTimeMs());
    }

    @Test
    void blocked_hasCorrectDecisionAndScore() {
        var blocked = new FraudResult.Blocked(90, List.of("IP_BLACKLISTED"), Duration.ofMillis(120));
        assertEquals(FraudDecision.BLOCK, blocked.decision());
        assertEquals("BLOCK", blocked.toScore().decision());
        assertEquals(90, blocked.toScore().score());
        assertEquals(List.of("IP_BLACKLISTED"), blocked.reasons());
        assertEquals(120L, blocked.toScore().analysisTimeMs());
    }
}
