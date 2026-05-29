package com.acaboumony.payment.repository;

import com.acaboumony.payment.domain.entity.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    Optional<Transaction> findByTransactionId(String transactionId);
    Optional<Transaction> findByIdempotencyKey(UUID idempotencyKey);
    Page<Transaction> findByCustomerIdOrderByCreatedAtDesc(UUID customerId, Pageable pageable);
}
