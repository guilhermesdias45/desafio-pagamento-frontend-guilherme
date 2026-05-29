package com.acaboumony.payment.repository;

import com.acaboumony.payment.domain.entity.Refund;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RefundRepository extends JpaRepository<Refund, Long> {
    List<Refund> findByTransactionIdOrderByCreatedAtDesc(String transactionId);
    Optional<Refund> findByIdempotencyKey(UUID idempotencyKey);
}
