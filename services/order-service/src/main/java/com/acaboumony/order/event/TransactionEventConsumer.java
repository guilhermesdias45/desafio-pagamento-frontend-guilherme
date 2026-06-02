package com.acaboumony.order.event;

import com.acaboumony.order.event.payload.TransactionCompletedEvent;
import com.acaboumony.order.event.payload.TransactionFailedEvent;
import com.acaboumony.order.event.payload.TransactionRefundedEvent;
import com.acaboumony.order.service.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer for transaction lifecycle events published by payment-service.
 *
 * <p>Listens to {@code transaction.completed}, {@code transaction.failed}, and
 * {@code transaction.refunded} topics and transitions the corresponding order state.</p>
 */
@Component
public class TransactionEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(TransactionEventConsumer.class);

    private final OrderService orderService;

    public TransactionEventConsumer(OrderService orderService) {
        this.orderService = orderService;
    }

    @KafkaListener(topics = "transaction.completed", groupId = "${spring.kafka.consumer.group-id}")
    public void onTransactionCompleted(TransactionCompletedEvent event) {
        log.info("Received transaction.completed transactionId={} orderId={}",
                event.transactionId(), event.orderId());
        orderService.markOrderPaid(event.orderId(), event.transactionId());
    }

    @KafkaListener(topics = "transaction.failed", groupId = "${spring.kafka.consumer.group-id}")
    public void onTransactionFailed(TransactionFailedEvent event) {
        log.info("Received transaction.failed transactionId={} orderId={} errorCode={}",
                event.transactionId(), event.orderId(), event.errorCode());
        orderService.markOrderFailed(event.orderId());
    }

    @KafkaListener(topics = "transaction.refunded", groupId = "${spring.kafka.consumer.group-id}")
    public void onTransactionRefunded(TransactionRefundedEvent event) {
        log.info("Received transaction.refunded transactionId={} orderId={} fullRefund={}",
                event.transactionId(), event.orderId(), event.fullRefund());
        orderService.markOrderRefunded(event.orderId(), event.fullRefund());
    }
}
