package com.acaboumony.user.repository;

import com.acaboumony.user.domain.entity.UserAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface UserAuditLogRepository extends JpaRepository<UserAuditLog, UUID> {
    // Basic CRUD from JpaRepository is sufficient for Sprint 1.
    // Advanced queries (e.g. findByUserId paginated) are Sprint 2.
}
