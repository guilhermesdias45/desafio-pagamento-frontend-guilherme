package com.acaboumony.user.domain.entity;

import com.acaboumony.user.domain.enums.MerchantStatus;
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
 * JPA entity for the {@code merchants} table (V2__create_merchants.sql).
 */
@Entity
@Table(name = "merchants")
@Getter
@NoArgsConstructor
@ToString(exclude = {"owner"})
public class Merchant {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "company_name", nullable = false, length = 100)
    private String companyName;

    @Column(nullable = false, unique = true, length = 14)
    private String cnpj;

    /** FK to the user who owns this merchant account. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "merchant_status")
    private MerchantStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Builder
    public Merchant(String companyName, String cnpj, User owner, MerchantStatus status) {
        this.companyName = companyName;
        this.cnpj = cnpj;
        this.owner = owner;
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
            this.status = MerchantStatus.ACTIVE;
        }
    }

    public void setStatus(MerchantStatus status) {
        this.status = status;
        this.updatedAt = Instant.now();
    }
}
