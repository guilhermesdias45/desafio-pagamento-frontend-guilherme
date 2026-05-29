package com.acaboumony.user.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity for the {@code user_audit_logs} table (V4__create_user_audit_logs.sql).
 *
 * <p>Deliberately does NOT have a {@code @ManyToOne} to {@link User} — {@code userId} is stored as
 * a plain {@code UUID} to avoid circular dependencies and to allow audit records for events with
 * no associated user (e.g. failed login with non-existent email).</p>
 */
@Entity
@Table(name = "user_audit_logs")
@Getter
@NoArgsConstructor
public class UserAuditLog {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    /** Nullable: login attempts for non-existent emails have no associated user. */
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "device_fingerprint", length = 255)
    private String deviceFingerprint;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Builder
    public UserAuditLog(UUID userId, String eventType, String ipAddress, String deviceFingerprint) {
        this.userId = userId;
        this.eventType = eventType;
        this.ipAddress = ipAddress;
        this.deviceFingerprint = deviceFingerprint;
    }

    @PrePersist
    void prePersist() {
        if (this.id == null) {
            this.id = UUID.randomUUID();
        }
        this.createdAt = Instant.now();
    }
}
