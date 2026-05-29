package com.acaboumony.order.service;

import com.acaboumony.order.domain.enums.OrderStatus;
import com.acaboumony.order.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

@Component
public class OrderExpirationService {

    private static final Logger log = LoggerFactory.getLogger(OrderExpirationService.class);

    private final OrderRepository orderRepository;

    public OrderExpirationService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Scheduled(fixedDelay = 60, timeUnit = TimeUnit.SECONDS)
    @Transactional
    public void expireStaleOrders() {
        var expiredOrders = orderRepository
                .findByStatusAndExpiresAtBefore(OrderStatus.PENDING, Instant.now());

        if (expiredOrders.isEmpty()) {
            return;
        }

        log.info("Expiring {} stale order(s)", expiredOrders.size());
        for (var order : expiredOrders) {
            order.setStatus(OrderStatus.CANCELLED);
            order.setUpdatedAt(Instant.now());
            order.setExpiresAt(null);
            orderRepository.save(order);
            log.info("Order {} expired and set to CANCELLED", order.getId());
        }
    }
}
