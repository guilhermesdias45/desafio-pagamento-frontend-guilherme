package com.acaboumony.notification.consumer;

import com.acaboumony.notification.dto.event.TransactionCompletedEvent;
import com.acaboumony.notification.dto.event.TransactionFailedEvent;
import com.acaboumony.notification.dto.event.TransactionRefundedEvent;
import com.acaboumony.notification.service.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class TransactionEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(TransactionEventConsumer.class);

    private final EmailService emailService;

    public TransactionEventConsumer(EmailService emailService) {
        this.emailService = emailService;
    }

    @KafkaListener(topics = "transaction.completed", groupId = "notification-service-group")
    public void consumeTransactionCompleted(TransactionCompletedEvent event) {
        log.info("Received transaction.completed event for transactionId={}", event.transactionId());

        var formattedAmount = String.format("R$ %.2f", event.amountInCents() / 100.0);
        var itemsHtml = event.items() != null ? event.items().stream()
                .map(i -> String.format("%s x%d — R$ %.2f",
                        i.description(), i.quantity(), (i.unitPriceInCents() * i.quantity()) / 100.0))
                .reduce((a, b) -> a + "<br/>" + b)
                .orElse("") : "";

        if (event.customerEmail() != null) {
            emailService.sendEmail(
                    event.customerEmail(),
                    "Pagamento confirmado — Pedido #" + event.orderId(),
                    "payment-confirmed-customer",
                    Map.of(
                            "formattedAmount", formattedAmount,
                            "cardBrand", event.cardBrand(),
                            "cardLastFour", event.cardLastFour(),
                            "installments", event.installments(),
                            "itemsHtml", itemsHtml,
                            "orderId", event.orderId().toString()
                    ),
                    event.transactionId() + "-customer"
            );
        }

        if (event.merchantEmail() != null) {
            emailService.sendEmail(
                    event.merchantEmail(),
                    "Nova venda confirmada — " + formattedAmount,
                    "payment-confirmed-merchant",
                    Map.of(
                            "formattedAmount", formattedAmount,
                            "itemsHtml", itemsHtml,
                            "orderId", event.orderId().toString()
                    ),
                    event.transactionId() + "-merchant"
            );
        }
    }

    @KafkaListener(topics = "transaction.failed", groupId = "notification-service-group")
    public void consumeTransactionFailed(TransactionFailedEvent event) {
        log.info("Received transaction.failed event for transactionId={}", event.transactionId());

        var formattedAmount = String.format("R$ %.2f", event.amountInCents() / 100.0);
        emailService.sendEmail(
                event.customerEmail(),
                "Pagamento não aprovado — " + formattedAmount,
                "payment-failed",
                Map.of(
                        "formattedAmount", formattedAmount,
                        "reason", event.reason() != null ? event.reason() : "Motivo não informado"
                ),
                event.transactionId()
        );
    }

    @KafkaListener(topics = "transaction.refunded", groupId = "notification-service-group")
    public void consumeTransactionRefunded(TransactionRefundedEvent event) {
        log.info("Received transaction.refunded event for transactionId={}", event.transactionId());

        var formattedAmount = String.format("R$ %.2f", event.amountRefundedInCents() / 100.0);
        emailService.sendEmail(
                event.customerEmail(),
                "Estorno processado — " + formattedAmount,
                "refund-confirmed",
                Map.of(
                        "formattedAmount", formattedAmount,
                        "estimatedArrivalDays", event.estimatedArrivalDays() != null
                                ? event.estimatedArrivalDays() : 5,
                        "refundId", event.refundId()
                ),
                event.refundId()
        );
    }
}
