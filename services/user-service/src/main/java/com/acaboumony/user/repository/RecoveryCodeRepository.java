package com.acaboumony.user.repository;

import com.acaboumony.user.domain.entity.RecoveryCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RecoveryCodeRepository extends JpaRepository<RecoveryCode, UUID> {

    List<RecoveryCode> findByUserIdAndUsedFalse(UUID userId);

    long countByUserIdAndUsedFalse(UUID userId);

    void deleteByUserId(UUID userId);
}
