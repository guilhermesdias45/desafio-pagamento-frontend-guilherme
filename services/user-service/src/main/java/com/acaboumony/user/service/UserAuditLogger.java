package com.acaboumony.user.service;

import com.acaboumony.user.domain.entity.UserAuditLog;
import com.acaboumony.user.repository.UserAuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Persists security audit events to the {@code user_audit_logs} table.
 *
 * <p>Valid {@code eventType} values are constrained by the CHECK constraint in
 * V4__create_user_audit_logs.sql.</p>
 */
@Service
public class UserAuditLogger {

    private static final Logger log = LoggerFactory.getLogger(UserAuditLogger.class);

    private final UserAuditLogRepository auditLogRepository;

    public UserAuditLogger(UserAuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    /**
     * Persists an audit log entry.
     *
     * @param userId            user UUID (nullable for events with non-existent emails)
     * @param eventType         one of the values in V4 CHECK constraint
     * @param ipAddress         client IP address (IPv4/IPv6), or {@code null}
     * @param deviceFingerprint device fingerprint string, or {@code null}
     */
    public void log(UUID userId, String eventType, String ipAddress, String deviceFingerprint) {
        try {
            UserAuditLog entry = UserAuditLog.builder()
                    .userId(userId)
                    .eventType(eventType)
                    .ipAddress(ipAddress)
                    .deviceFingerprint(deviceFingerprint)
                    .build();
            auditLogRepository.save(entry);
            log.debug("Audit log: userId={}, eventType={}", userId, eventType);
        } catch (Exception e) {
            // Audit logging must never fail the main flow
            log.error("Failed to write audit log: userId={}, eventType={}: {}", userId, eventType, e.getMessage());
        }
    }
}
