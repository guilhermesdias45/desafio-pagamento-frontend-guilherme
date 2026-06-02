package com.acaboumony.payment.domain.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "audit_logs")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transaction_id", length = 64)
    private String transactionId;

    @Column(name = "action", nullable = false, length = 64)
    private String action;

    @Column(name = "actor_id")
    private UUID actorId;

    @Column(name = "payload", columnDefinition = "jsonb")
    private String payload;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public AuditLog() {}

    public AuditLog(String transactionId, String action, UUID actorId, String payload, String ipAddress) {
        this.transactionId = transactionId;
        this.action = action;
        this.actorId = actorId;
        this.payload = payload;
        this.ipAddress = ipAddress;
    }

    public Long getId() { return id; }
    public String getTransactionId() { return transactionId; }
    public String getAction() { return action; }
    public UUID getActorId() { return actorId; }
    public String getPayload() { return payload; }
    public String getIpAddress() { return ipAddress; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
