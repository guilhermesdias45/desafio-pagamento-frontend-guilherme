package com.acaboumony.fraud.domain.entity;

import com.acaboumony.fraud.domain.enums.FraudDecision;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * Persisted record of a fraud analysis result.
 * Only BLOCK and REVIEW decisions are stored.
 */
@Entity
@Table(name = "fraud_alerts")
public class FraudAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "transaction_id", nullable = false, length = 64)
    private String transactionId;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "amount_in_cents", nullable = false)
    private Long amountInCents;

    @Column(nullable = false)
    private Integer score;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "fraud_decision")
    private FraudDecision decision;

    @Column
    private String reasons;

    @Column(name = "analysis_time_ms")
    private Long analysisTimeMs;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        this.createdAt = Instant.now();
    }

    // --- Constructors ---

    protected FraudAlert() {}

    public FraudAlert(String transactionId,
                      UUID customerId,
                      Long amountInCents,
                      Integer score,
                      FraudDecision decision,
                      String reasons,
                      Long analysisTimeMs,
                      String ipAddress) {
        this.transactionId = transactionId;
        this.customerId = customerId;
        this.amountInCents = amountInCents;
        this.score = score;
        this.decision = decision;
        this.reasons = reasons;
        this.analysisTimeMs = analysisTimeMs;
        this.ipAddress = ipAddress;
    }

    // --- Getters ---

    public UUID getId() { return id; }
    public String getTransactionId() { return transactionId; }
    public UUID getCustomerId() { return customerId; }
    public Long getAmountInCents() { return amountInCents; }
    public Integer getScore() { return score; }
    public FraudDecision getDecision() { return decision; }
    public String getReasons() { return reasons; }
    public Long getAnalysisTimeMs() { return analysisTimeMs; }
    public String getIpAddress() { return ipAddress; }
    public Instant getCreatedAt() { return createdAt; }
}
