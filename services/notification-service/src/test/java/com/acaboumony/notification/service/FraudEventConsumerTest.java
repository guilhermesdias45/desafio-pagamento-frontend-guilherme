package com.acaboumony.notification.service;

import com.acaboumony.notification.consumer.FraudEventConsumer;
import com.acaboumony.notification.dto.event.FraudDetectedEvent;
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
class FraudEventConsumerTest {

    @Mock
    private EmailService emailService;

    private FraudEventConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new FraudEventConsumer(emailService, "security@acaboumony.com");
    }

    @Test
    void shouldSendFraudAlertEmail() {
        var event = new FraudDetectedEvent(
                "txn_123", UUID.randomUUID(), 85, "REJECT",
                List.of("HIGH_AMOUNT", "NEW_DEVICE"),
                Instant.now()
        );

        consumer.consumeFraudDetected(event);

        verify(emailService).sendEmail(
                eq("security@acaboumony.com"), anyString(), eq("fraud-alert"), any(), anyString()
        );
    }

    @Test
    void shouldSkipWhenSecurityEmailIsBlank() {
        consumer = new FraudEventConsumer(emailService, "");
        var event = new FraudDetectedEvent(
                "txn_456", UUID.randomUUID(), 50, "REVIEW",
                List.of(), Instant.now()
        );

        consumer.consumeFraudDetected(event);

        verify(emailService, never()).sendEmail(any(), any(), any(), any(), any());
    }
}
