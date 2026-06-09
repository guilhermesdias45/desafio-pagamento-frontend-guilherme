package com.acaboumony.payment.domain.entity;

import com.acaboumony.payment.domain.enums.MpAccountType;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "mp_test_accounts")
public class MpTestAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 16)
    private MpAccountType type;

    @Column(name = "mp_user_id", nullable = false)
    private Long mpUserId;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "password_enc", columnDefinition = "TEXT")
    private String passwordEnc;

    @Column(name = "verification_code", length = 16)
    private String verificationCode;

    @Column(name = "access_token_enc", columnDefinition = "TEXT")
    private String accessTokenEnc;

    @Column(name = "refresh_token_enc", columnDefinition = "TEXT")
    private String refreshTokenEnc;

    @Column(name = "public_key", length = 255)
    private String publicKey;

    @Column(name = "token_expires_at")
    private Instant tokenExpiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        if (this.id == null) {
            this.id = UUID.randomUUID();
        }
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = Instant.now();
    }

    public MpTestAccount() {}

    public MpTestAccount(MpAccountType type, Long mpUserId, String email,
                         String passwordEnc, String verificationCode) {
        this.type = type;
        this.mpUserId = mpUserId;
        this.email = email;
        this.passwordEnc = passwordEnc;
        this.verificationCode = verificationCode;
    }

    public UUID getId() { return id; }
    public MpAccountType getType() { return type; }
    public Long getMpUserId() { return mpUserId; }
    public String getEmail() { return email; }
    public String getPasswordEnc() { return passwordEnc; }
    public String getVerificationCode() { return verificationCode; }
    public String getAccessTokenEnc() { return accessTokenEnc; }
    public String getRefreshTokenEnc() { return refreshTokenEnc; }
    public String getPublicKey() { return publicKey; }
    public Instant getTokenExpiresAt() { return tokenExpiresAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setAccessTokenEnc(String accessTokenEnc) { this.accessTokenEnc = accessTokenEnc; }
    public void setRefreshTokenEnc(String refreshTokenEnc) { this.refreshTokenEnc = refreshTokenEnc; }
    public void setPublicKey(String publicKey) { this.publicKey = publicKey; }
    public void setTokenExpiresAt(Instant tokenExpiresAt) { this.tokenExpiresAt = tokenExpiresAt; }
}
