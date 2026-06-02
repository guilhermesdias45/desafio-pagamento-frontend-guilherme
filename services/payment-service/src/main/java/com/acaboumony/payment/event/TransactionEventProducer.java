package com.acaboumony.payment.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Publishes transaction domain events to Kafka topics.
 * Topics: transaction.completed, transaction.failed, transaction.refunded
 */
@Component
public class TransactionEventProducer {

    private static final Logger log = LoggerFactory.getLogger(TransactionEventProducer.class);

    static final String TOPIC_COMPLETED = "transaction.completed";
    static final String TOPIC_FAILED = "transaction.failed";
    static final String TOPIC_REFUNDED = "transaction.refunded";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public TransactionEventProducer(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishTransactionCompleted(String transactionId, UUID orderId, UUID customerId, long amountInCents) {
        TransactionCompletedEvent event = new TransactionCompletedEvent(
                transactionId, orderId, customerId, amountInCents, Instant.now()
        );
        kafkaTemplate.send(TOPIC_COMPLETED, transactionId, event);
        log.info("Published transaction.completed event: transactionId={}", transactionId);
    }

    public void publishTransactionFailed(String transactionId, UUID orderId, UUID customerId, String errorCode) {
        TransactionFailedEvent event = new TransactionFailedEvent(
                transactionId, orderId, customerId, errorCode, Instant.now()
        );
        kafkaTemplate.send(TOPIC_FAILED, transactionId, event);
        log.info("Published transaction.failed event: transactionId={} errorCode={}", transactionId, errorCode);
    }

    public void publishTransactionRefunded(String transactionId, UUID orderId, UUID customerId,
                                           long refundedAmountInCents, boolean fullRefund) {
        TransactionRefundedEvent event = new TransactionRefundedEvent(
                transactionId, orderId, customerId, refundedAmountInCents, fullRefund, Instant.now()
        );
        kafkaTemplate.send(TOPIC_REFUNDED, transactionId, event);
        log.info("Published transaction.refunded event: transactionId={} fullRefund={}", transactionId, fullRefund);
    }
}
