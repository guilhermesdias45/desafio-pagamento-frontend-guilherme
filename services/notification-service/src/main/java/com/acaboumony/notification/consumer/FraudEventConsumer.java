package com.acaboumony.notification.consumer;

import com.acaboumony.notification.dto.event.FraudDetectedEvent;
import com.acaboumony.notification.service.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class FraudEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(FraudEventConsumer.class);

    private final EmailService emailService;
    private final String securityAlertEmail;

    public FraudEventConsumer(EmailService emailService,
                              @Value("${security.alert.email:}") String securityAlertEmail) {
        this.emailService = emailService;
        this.securityAlertEmail = securityAlertEmail;
    }

    @KafkaListener(topics = "fraud.detected", groupId = "notification-service-group")
    public void consumeFraudDetected(FraudDetectedEvent event) {
        log.info("Received fraud.detected event for transactionId={}, score={}", event.transactionId(), event.score());

        if (securityAlertEmail == null || securityAlertEmail.isBlank()) {
            log.warn("SECURITY_ALERT_EMAIL not configured, skipping fraud alert");
            return;
        }

        emailService.sendEmail(
                securityAlertEmail,
                "[FRAUD ALERT] Score " + event.score() + " — Transaction blocked",
                "fraud-alert",
                Map.of(
                        "transactionId", event.transactionId(),
                        "customerId", event.customerId().toString(),
                        "score", event.score(),
                        "reasons", String.join(", ", event.reasons()),
                        "detectedAt", event.detectedAt().toString()
                ),
                event.transactionId()
        );
    }
}
