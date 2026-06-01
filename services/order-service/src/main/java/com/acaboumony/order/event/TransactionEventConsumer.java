package com.acaboumony.order.event;

import com.acaboumony.order.domain.enums.OrderStatus;
import com.acaboumony.order.repository.OrderRepository;
import com.acaboumony.order.service.OrderCacheService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Component
public class TransactionEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(TransactionEventConsumer.class);

    private final OrderRepository orderRepository;
    private final OrderCacheService orderCacheService;

    public TransactionEventConsumer(OrderRepository orderRepository, OrderCacheService orderCacheService) {
        this.orderRepository = orderRepository;
        this.orderCacheService = orderCacheService;
    }

    @KafkaListener(topics = "transaction.completed", groupId = "order-service-group")
    @Transactional
    public void consumeTransactionCompleted(TransactionCompletedEvent event) {
        log.info("Received transaction.completed event: transactionId={}, orderId={}",
                event.transactionId(), event.orderId());

        var order = orderRepository.findById(event.orderId())
                .orElse(null);
        if (order == null) {
            log.warn("Order not found for transaction.completed event: orderId={}", event.orderId());
            return;
        }

        if (order.getStatus() == OrderStatus.PENDING) {
            order.setStatus(OrderStatus.PAID);
            order.setTransactionId(event.transactionId());
            order.setUpdatedAt(Instant.now());
            order.setExpiresAt(null);
            orderRepository.save(order);
            orderCacheService.evict(event.orderId());
            log.info("Order {} updated to PAID with transactionId={}", order.getId(), event.transactionId());
        } else {
            log.warn("Order {} is not in PENDING status (current={}), skipping transaction.completed",
                    order.getId(), order.getStatus());
        }
    }

    @KafkaListener(topics = "transaction.failed", groupId = "order-service-group")
    @Transactional
    public void consumeTransactionFailed(TransactionFailedEvent event) {
        log.info("Received transaction.failed event: transactionId={}, orderId={}, reason={}",
                event.transactionId(), event.orderId(), event.reason());

        var order = orderRepository.findById(event.orderId())
                .orElse(null);
        if (order == null) {
            log.warn("Order not found for transaction.failed event: orderId={}", event.orderId());
            return;
        }

        if (order.getStatus() == OrderStatus.PROCESSING || order.getStatus() == OrderStatus.PENDING) {
            order.setStatus(OrderStatus.PENDING);
            order.setUpdatedAt(Instant.now());
            orderRepository.save(order);
            orderCacheService.evict(event.orderId());
            log.info("Order {} returned to PENDING after transaction failure", order.getId());
        } else {
            log.warn("Order {} cannot be returned to PENDING (current={}), skipping transaction.failed",
                    order.getId(), order.getStatus());
        }
    }

    @KafkaListener(topics = "transaction.refunded", groupId = "order-service-group")
    @Transactional
    public void consumeTransactionRefunded(TransactionRefundedEvent event) {
        log.info("Received transaction.refunded event: transactionId={}, orderId={}, isFullRefund={}",
                event.transactionId(), event.orderId(), event.isFullRefund());

        var order = orderRepository.findById(event.orderId())
                .orElse(null);
        if (order == null) {
            log.warn("Order not found for transaction.refunded event: orderId={}", event.orderId());
            return;
        }

        var newStatus = Boolean.TRUE.equals(event.isFullRefund())
                ? OrderStatus.REFUNDED
                : OrderStatus.PARTIALLY_REFUNDED;
        order.setStatus(newStatus);
        order.setUpdatedAt(Instant.now());
        orderRepository.save(order);
        orderCacheService.evict(event.orderId());
        log.info("Order {} updated to {} after refund", order.getId(), newStatus);
    }
}
