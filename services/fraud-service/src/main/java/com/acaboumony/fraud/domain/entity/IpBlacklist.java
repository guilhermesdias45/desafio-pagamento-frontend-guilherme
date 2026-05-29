package com.acaboumony.fraud.domain.entity;

import com.acaboumony.fraud.domain.enums.BlacklistSource;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Entity
@Table(name = "ip_blacklist")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IpBlacklist {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "ip_address", nullable = false, length = 45)
    private String ipAddress;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @Column(nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private BlacklistSource source;

    @Column(name = "expires_at")
    private OffsetDateTime expiresAt;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now(ZoneOffset.UTC);
        }
    }
}
