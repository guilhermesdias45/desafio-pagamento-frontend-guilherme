package com.acaboumony.notification.service;

import com.acaboumony.notification.consumer.FraudEventConsumer;
import com.acaboumony.notification.consumer.OrderEventConsumer;
import com.acaboumony.notification.consumer.TransactionEventConsumer;
import com.acaboumony.notification.dto.event.FraudDetectedEvent;
import com.acaboumony.notification.dto.event.OrderCreatedEvent;
import com.acaboumony.notification.dto.event.TransactionCompletedEvent;
import com.acaboumony.notification.dto.event.TransactionFailedEvent;
import com.acaboumony.notification.dto.event.TransactionRefundedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TransactionEventConsumerTest {

    @Mock
    private EmailService emailService;

    private TransactionEventConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new TransactionEventConsumer(emailService);
    }

    @Test
    void shouldSendPaymentConfirmedEmailToCustomerAndMerchant() {
        var event = new TransactionCompletedEvent(
                "txn_123", 12345L, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "customer@test.com", "merchant@test.com",
                8990L, "BRL", "visa", "4242", 1,
                List.of(new TransactionCompletedEvent.ItemEvent("Item 1", 1, 8990L)),
                Instant.now()
        );

        consumer.consumeTransactionCompleted(event);

        verify(emailService).sendEmail(
                eq("customer@test.com"), anyString(), eq("payment-confirmed-customer"), any(), anyString()
        );
        verify(emailService).sendEmail(
                eq("merchant@test.com"), anyString(), eq("payment-confirmed-merchant"), any(), anyString()
        );
    }

    @Test
    void shouldSendFailedEmail() {
        var event = new TransactionFailedEvent(
                "txn_123", UUID.randomUUID(), UUID.randomUUID(),
                "customer@test.com", 8990L, "CARD_DECLINED", "2026-01-01T00:00:00Z"
        );

        consumer.consumeTransactionFailed(event);

        verify(emailService).sendEmail(
                eq("customer@test.com"), anyString(), eq("payment-failed"), any(), anyString()
        );
    }

    @Test
    void shouldSendRefundEmail() {
        var event = new TransactionRefundedEvent(
                "ref_1", "txn_123", UUID.randomUUID(),
                "customer@test.com", 8990L, true, "CUSTOMER_REQUEST",
                5, Instant.now()
        );

        consumer.consumeTransactionRefunded(event);

        verify(emailService).sendEmail(
                eq("customer@test.com"), anyString(), eq("refund-confirmed"), any(), anyString()
        );
    }
}
