package com.acaboumony.fraud.repository;

import com.acaboumony.fraud.domain.entity.FraudAlert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link FraudAlert} persistence.
 */
@Repository
public interface FraudAlertRepository extends JpaRepository<FraudAlert, UUID> {

    List<FraudAlert> findByCustomerId(UUID customerId);

    List<FraudAlert> findByTransactionId(String transactionId);
}
