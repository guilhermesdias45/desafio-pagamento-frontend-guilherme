package com.acaboumony.payment.repository;

import com.acaboumony.payment.domain.enums.MpAccountType;
import com.acaboumony.payment.domain.entity.MpTestAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface MpTestAccountRepository extends JpaRepository<MpTestAccount, UUID> {

    Optional<MpTestAccount> findByType(MpAccountType type);

    Optional<MpTestAccount> findByEmail(String email);

    boolean existsByType(MpAccountType type);
}
