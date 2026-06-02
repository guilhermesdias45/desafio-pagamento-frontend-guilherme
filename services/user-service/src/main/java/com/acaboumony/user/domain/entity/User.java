package com.acaboumony.user.domain.entity;

import com.acaboumony.user.domain.enums.UserRole;
import com.acaboumony.user.domain.enums.UserStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity for the {@code users} table (V1__create_users.sql).
 *
 * <p>Sensitive fields ({@code passwordHash}, {@code totpSecretEncrypted}) are excluded from
 * {@code toString()} to prevent accidental log leakage.</p>
 */
@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor
@ToString(exclude = {"passwordHash", "totpSecretEncrypted", "merchant"})
public class User {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    /** BCrypt hash — always 60 chars. Never logged or exposed in toString. */
    @Column(name = "password_hash", nullable = false, length = 60)
    private String passwordHash;

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "user_role")
    private UserRole role;

    /**
     * Nullable FK to {@link Merchant}. Populated only for {@code MERCHANT_OWNER} users.
     * FK constraint added in V2__create_merchants.sql.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "merchant_id", nullable = true)
    private Merchant merchant;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "user_status")
    private UserStatus status;

    @Column(name = "totp_enabled", nullable = false)
    private boolean totpEnabled;

    /** AES-256-GCM encrypted TOTP secret. {@code null} until 2FA is confirmed. */
    @Column(name = "totp_secret_encrypted", columnDefinition = "text")
    private String totpSecretEncrypted;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Builder
    public User(String email, String passwordHash, String fullName,
                UserRole role, UserStatus status) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.fullName = fullName;
        this.role = role;
        this.status = status;
    }

    @PrePersist
    void prePersist() {
        if (this.id == null) {
            this.id = UUID.randomUUID();
        }
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.status == null) {
            this.status = UserStatus.PENDING_EMAIL_CONFIRMATION;
        }
    }

    // ─── Setters for mutable fields only ────────────────────────────────────

    public void setStatus(UserStatus status) {
        this.status = status;
        this.updatedAt = Instant.now();
    }

    public void setMerchant(Merchant merchant) {
        this.merchant = merchant;
        this.updatedAt = Instant.now();
    }

    public void setTotpEnabled(boolean totpEnabled) {
        this.totpEnabled = totpEnabled;
        this.updatedAt = Instant.now();
    }

    public void setTotpSecretEncrypted(String totpSecretEncrypted) {
        this.totpSecretEncrypted = totpSecretEncrypted;
        this.updatedAt = Instant.now();
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
        this.updatedAt = Instant.now();
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
        this.updatedAt = Instant.now();
    }
}
