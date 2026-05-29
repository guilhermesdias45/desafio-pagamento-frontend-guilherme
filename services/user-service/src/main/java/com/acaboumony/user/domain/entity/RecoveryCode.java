package com.acaboumony.user.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity for the {@code recovery_codes} table (V3__create_recovery_codes.sql).
 *
 * <p>{@code codeHash} is a BCrypt hash — never logged or exposed.</p>
 */
@Entity
@Table(name = "recovery_codes")
@Getter
@NoArgsConstructor
@ToString(exclude = {"codeHash"})
public class RecoveryCode {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** BCrypt hash of the plaintext recovery code. */
    @Column(name = "code_hash", nullable = false, length = 60)
    private String codeHash;

    @Column(nullable = false)
    private boolean used;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Builder
    public RecoveryCode(User user, String codeHash) {
        this.user = user;
        this.codeHash = codeHash;
        this.used = false;
    }

    @PrePersist
    void prePersist() {
        if (this.id == null) {
            this.id = UUID.randomUUID();
        }
        this.createdAt = Instant.now();
    }

    public void markUsed() {
        this.used = true;
        this.usedAt = Instant.now();
    }
}
