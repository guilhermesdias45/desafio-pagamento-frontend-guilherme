package com.acaboumony.payment.repository;

import com.acaboumony.payment.domain.entity.Refund;
import com.acaboumony.payment.domain.enums.RefundStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefundRepository extends JpaRepository<Refund, UUID> {

    List<Refund> findByTransactionId(String transactionId);

    Optional<Refund> findByIdempotencyKey(UUID idempotencyKey);

    @Query("SELECT COALESCE(SUM(r.amountInCents), 0) FROM Refund r WHERE r.transactionId = :transactionId AND r.status = :status")
    Long sumAmountByTransactionIdAndStatus(@Param("transactionId") String transactionId, @Param("status") RefundStatus status);
}
