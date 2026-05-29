package com.acaboumony.payment.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class TransactionEventProducer {

    private static final Logger log = LoggerFactory.getLogger(TransactionEventProducer.class);

    static final String TOPIC_COMPLETED = "transaction.completed";
    static final String TOPIC_FAILED = "transaction.failed";
    static final String TOPIC_REFUNDED = "transaction.refunded";

    private final KafkaTemplate<String, Object> kafka;

    public TransactionEventProducer(KafkaTemplate<String, Object> kafka) {
        this.kafka = kafka;
    }

    public void publishCompleted(TransactionCompletedEvent event) {
        kafka.send(TOPIC_COMPLETED, event.transactionId(), event)
            .whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("Failed to publish transaction.completed for transaction {}",
                        event.transactionId(), ex);
                }
            });
    }

    public void publishFailed(TransactionFailedEvent event) {
        kafka.send(TOPIC_FAILED, event.transactionId(), event)
            .whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("Failed to publish transaction.failed for transaction {}",
                        event.transactionId(), ex);
                }
            });
    }

    public void publishRefunded(TransactionRefundedEvent event) {
        kafka.send(TOPIC_REFUNDED, event.transactionId(), event)
            .whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("Failed to publish transaction.refunded for refund {}",
                        event.refundId(), ex);
                }
            });
    }
}
