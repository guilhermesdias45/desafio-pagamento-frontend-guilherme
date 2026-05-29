package com.acaboumony.fraud.result;

import com.acaboumony.fraud.domain.enums.FraudDecision;
import com.acaboumony.fraud.dto.response.FraudScore;

import java.time.Duration;
import java.util.List;

public sealed interface FraudResult permits FraudResult.Approved, FraudResult.UnderReview, FraudResult.Blocked {

    FraudDecision decision();
    FraudScore toScore();
    List<String> reasons();

    record Approved(int score, List<String> reasons, Duration analysisTime) implements FraudResult {
        @Override
        public FraudDecision decision() {
            return FraudDecision.APPROVE;
        }

        @Override
        public FraudScore toScore() {
            return new FraudScore(score, "APPROVE", reasons, analysisTime.toMillis());
        }
    }

    record UnderReview(int baseScore, int adjustedScore, List<String> reasons, Duration analysisTime, int claudeAdjustment) implements FraudResult {
        @Override
        public FraudDecision decision() {
            return FraudDecision.REVIEW;
        }

        @Override
        public FraudScore toScore() {
            return new FraudScore(adjustedScore, "REVIEW", reasons, analysisTime.toMillis());
        }
    }

    record Blocked(int score, List<String> reasons, Duration analysisTime) implements FraudResult {
        @Override
        public FraudDecision decision() {
            return FraudDecision.BLOCK;
        }

        @Override
        public FraudScore toScore() {
            return new FraudScore(score, "BLOCK", reasons, analysisTime.toMillis());
        }
    }
}
