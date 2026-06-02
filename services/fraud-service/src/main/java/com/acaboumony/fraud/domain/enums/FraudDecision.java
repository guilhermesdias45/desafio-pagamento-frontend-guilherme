package com.acaboumony.fraud.domain.enums;

/**
 * Outcome of the deterministic fraud rule evaluation.
 *
 * <ul>
 *   <li>{@code APPROVE} — score below 70; transaction proceeds normally.</li>
 *   <li>{@code REVIEW}  — score between 70 and 89 inclusive; requires manual review.</li>
 *   <li>{@code BLOCK}   — score ≥ 90; transaction is automatically rejected.</li>
 * </ul>
 */
public enum FraudDecision {
    APPROVE,
    REVIEW,
    BLOCK
}
