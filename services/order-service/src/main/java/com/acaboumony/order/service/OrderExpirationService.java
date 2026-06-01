package com.acaboumony.order.service;

import com.acaboumony.order.domain.enums.OrderStatus;
import com.acaboumony.order.event.OrderCancelledEvent;
import com.acaboumony.order.event.OrderEventProducer;
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
    private final OrderEventProducer orderEventProducer;

    public OrderExpirationService(OrderRepository orderRepository, OrderEventProducer orderEventProducer) {
        this.orderRepository = orderRepository;
        this.orderEventProducer = orderEventProducer;
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
        var now = Instant.now();
        for (var order : expiredOrders) {
            order.setStatus(OrderStatus.CANCELLED);
            order.setUpdatedAt(now);
            order.setExpiresAt(null);
            orderRepository.save(order);

            var event = new OrderCancelledEvent(
                    order.getId(), order.getCustomerId(), null, order.getMerchantId(),
                    order.getTotalInCents(), "Order expired after 15 minutes", now
            );
            orderEventProducer.publishOrderCancelled(event);

            log.info("Order {} expired and set to CANCELLED", order.getId());
        }
    }
}
