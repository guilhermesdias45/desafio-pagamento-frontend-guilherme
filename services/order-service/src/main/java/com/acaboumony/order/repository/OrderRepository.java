package com.acaboumony.order.repository;

import com.acaboumony.order.domain.entity.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link Order} entities.
 */
public interface OrderRepository extends JpaRepository<Order, UUID> {

    Optional<Order> findByIdempotencyKey(UUID idempotencyKey);

    @Query("SELECT o FROM Order o WHERE o.customerId = :customerId ORDER BY o.createdAt DESC")
    Page<Order> findByCustomerId(@Param("customerId") UUID customerId, Pageable pageable);

    @Query("SELECT o FROM Order o WHERE o.merchantId = :merchantId ORDER BY o.createdAt DESC")
    Page<Order> findByMerchantId(@Param("merchantId") UUID merchantId, Pageable pageable);

    @Query("SELECT o FROM Order o WHERE o.status = com.acaboumony.order.domain.enums.OrderStatus.PENDING AND o.expiresAt < :now")
    List<Order> findExpiredPendingOrders(@Param("now") Instant now);
}
