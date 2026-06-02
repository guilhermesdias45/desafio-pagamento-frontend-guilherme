package com.acaboumony.order.service;

import com.acaboumony.order.domain.entity.Order;
import com.acaboumony.order.domain.enums.OrderStatus;
import com.acaboumony.order.event.OrderEventProducer;
import com.acaboumony.order.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Scheduled service that cancels PENDING orders whose expiration time has passed.
 *
 * <p>Runs every 60 seconds. Expired orders are transitioned to CANCELLED and a
 * {@code order.cancelled} Kafka event is published for downstream services.</p>
 */
@Service
public class OrderExpirationService {

    private static final Logger log = LoggerFactory.getLogger(OrderExpirationService.class);

    private final OrderRepository orderRepository;
    private final OrderEventProducer eventProducer;

    public OrderExpirationService(OrderRepository orderRepository, OrderEventProducer eventProducer) {
        this.orderRepository = orderRepository;
        this.eventProducer = eventProducer;
    }

    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void cancelExpiredOrders() {
        List<Order> expired = orderRepository.findExpiredPendingOrders(Instant.now());
        for (Order order : expired) {
            order.setStatus(OrderStatus.CANCELLED);
            orderRepository.save(order);
            eventProducer.publishOrderCancelled(order.getId(), order.getCustomerId(), "EXPIRATION");
            log.info("Cancelled expired order orderId={}", order.getId());
        }
        if (!expired.isEmpty()) {
            log.info("Expired order sweep completed: {} order(s) cancelled", expired.size());
        }
    }
}
