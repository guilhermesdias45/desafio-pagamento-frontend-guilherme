package com.acaboumony.payment.domain.entity;

import com.acaboumony.payment.domain.enums.RefundReason;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refunds")
public class Refund {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "refund_id", nullable = false, unique = true, length = 64)
    private String refundId;

    @Column(name = "transaction_id", nullable = false, length = 64)
    private String transactionId;

    @Column(name = "amount_in_cents", nullable = false)
    private Long amountInCents;

    @Column(name = "is_full_refund", nullable = false)
    private Boolean isFullRefund;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false, length = 64)
    private RefundReason reason;

    @Column(name = "requested_by", nullable = false)
    private UUID requestedBy;

    @Column(name = "idempotency_key", nullable = false, unique = true)
    private UUID idempotencyKey;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "estimated_arrival_days")
    private Integer estimatedArrivalDays;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        if (processedAt == null) processedAt = Instant.now();
    }

    public Refund() {}

    public Refund(String refundId, String transactionId, Long amountInCents,
                  Boolean isFullRefund, RefundReason reason, UUID requestedBy,
                  UUID idempotencyKey, String status) {
        this.refundId = refundId;
        this.transactionId = transactionId;
        this.amountInCents = amountInCents;
        this.isFullRefund = isFullRefund;
        this.reason = reason;
        this.requestedBy = requestedBy;
        this.idempotencyKey = idempotencyKey;
        this.status = status;
    }

    public Long getId() { return id; }
    public String getRefundId() { return refundId; }
    public String getTransactionId() { return transactionId; }
    public Long getAmountInCents() { return amountInCents; }
    public Boolean getIsFullRefund() { return isFullRefund; }
    public RefundReason getReason() { return reason; }
    public UUID getRequestedBy() { return requestedBy; }
    public UUID getIdempotencyKey() { return idempotencyKey; }
    public String getStatus() { return status; }
    public Integer getEstimatedArrivalDays() { return estimatedArrivalDays; }
    public Instant getProcessedAt() { return processedAt; }
    public Instant getCreatedAt() { return createdAt; }

    public void setStatus(String status) { this.status = status; }
    public void setEstimatedArrivalDays(Integer estimatedArrivalDays) { this.estimatedArrivalDays = estimatedArrivalDays; }
    public void setProcessedAt(Instant processedAt) { this.processedAt = processedAt; }
}
