package com.acaboumony.notification.service;

import com.acaboumony.notification.domain.entity.NotificationLog;
import com.acaboumony.notification.exception.EmailDeliveryException;
import com.acaboumony.notification.repository.NotificationLogRepository;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);
    private static final int MAX_RETRIES = 3;
    private static final long[] BACKOFF_MS = {1_000, 5_000, 30_000};

    private final JavaMailSender mailSender;
    private final SpringTemplateEngine templateEngine;
    private final NotificationLogRepository notificationLogRepository;
    private final EmailRateLimiter emailRateLimiter;

    public EmailService(JavaMailSender mailSender,
                        SpringTemplateEngine templateEngine,
                        NotificationLogRepository notificationLogRepository,
                        EmailRateLimiter emailRateLimiter) {
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
        this.notificationLogRepository = notificationLogRepository;
        this.emailRateLimiter = emailRateLimiter;
    }

    public void sendEmail(String to, String subject, String templateName,
                          Map<String, Object> templateVariables, String correlationId) {
        if (to == null || to.isBlank()) {
            log.warn("Skipping email to null/blank recipient: subject={}, template={}", subject, templateName);
            return;
        }
        if (isDuplicate(correlationId, templateName)) {
            log.info("Duplicate notification skipped: correlationId={}, eventType={}", correlationId, templateName);
            return;
        }
        if (emailRateLimiter.isRateLimited(to)) {
            log.warn("Rate limited: skipping email to {}, subject={}", to, subject);
            return;
        }

        sendWithRetry(to, subject, templateName, templateVariables, correlationId);
    }

    private boolean isDuplicate(String correlationId, String eventType) {
        if (correlationId == null) return false;
        return notificationLogRepository.findByCorrelationIdAndEventType(correlationId, eventType).isPresent();
    }

    private void sendWithRetry(String to, String subject, String templateName,
                               Map<String, Object> templateVariables, String correlationId) {
        Exception lastException = new RuntimeException("Failed to send email after " + MAX_RETRIES + " attempts");

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                sendMimeMessage(to, subject, templateName, templateVariables);
                markSuccess(to, templateName, correlationId);
                return;
            } catch (MailException | MessagingException e) {
                lastException = e;
                log.warn("Failed to send email (attempt {}/{}): to={}, error={}",
                        attempt, MAX_RETRIES, to, e.getMessage());

                if (attempt < MAX_RETRIES) {
                    try {
                        Thread.sleep(BACKOFF_MS[attempt - 1]);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new EmailDeliveryException(to, "Retry interrupted", false);
                    }
                }
            }
        }

        markFailed(to, templateName, correlationId, lastException.getMessage());
        throw new EmailDeliveryException(to, lastException.getMessage(), false);
    }

    private void sendMimeMessage(String to, String subject, String templateName,
                                 Map<String, Object> templateVariables) throws MessagingException {
        String htmlContent;
        try {
            var context = new Context();
            context.setVariables(templateVariables);
            htmlContent = templateEngine.process("email/" + templateName, context);
        } catch (Exception e) {
            log.warn("Template {} failed, falling back to plain text: {}", templateName, e.getMessage());
            htmlContent = buildPlainTextFallback(subject, templateVariables);
        }

        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(htmlContent, true);

        mailSender.send(message);
    }

    private String buildPlainTextFallback(String subject, Map<String, Object> variables) {
        var sb = new StringBuilder();
        sb.append(subject).append("\n\n");
        variables.forEach((key, value) -> {
            if (value != null) {
                sb.append(key).append(": ").append(value).append("\n");
            }
        });
        sb.append("\n---\nEsta é uma mensagem automática do Acabou o Mony.");
        return sb.toString().replace("\n", "<br/>");
    }

    private void markSuccess(String to, String eventType, String correlationId) {
        var logEntry = new NotificationLog(
                UUID.randomUUID(), eventType, to, "SENT", correlationId, null, Instant.now()
        );
        notificationLogRepository.save(logEntry);
        log.info("Email sent successfully: to={}, eventType={}", to, eventType);
    }

    private void markFailed(String to, String eventType, String correlationId, String errorMessage) {
        var logEntry = new NotificationLog(
                UUID.randomUUID(), eventType, to, "FAILED", correlationId, errorMessage, Instant.now()
        );
        notificationLogRepository.save(logEntry);
        log.error("Email failed permanently: to={}, eventType={}, error={}", to, eventType, errorMessage);
    }
}
