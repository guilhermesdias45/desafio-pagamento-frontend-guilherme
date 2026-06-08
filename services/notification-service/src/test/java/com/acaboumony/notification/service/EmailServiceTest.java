package com.acaboumony.notification.service;

import com.acaboumony.notification.domain.entity.NotificationLog;
import com.acaboumony.notification.exception.EmailDeliveryException;
import com.acaboumony.notification.repository.NotificationLogRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    private JavaMailSender mailSender;
    @Mock
    private SpringTemplateEngine templateEngine;
    @Mock
    private NotificationLogRepository notificationLogRepository;
    @Mock
    private EmailRateLimiter emailRateLimiter;

    private final MeterRegistry meterRegistry = new SimpleMeterRegistry();

    private EmailService emailService;

    @BeforeEach
    void setUp() {
        emailService = new EmailService(mailSender, templateEngine, notificationLogRepository, emailRateLimiter, meterRegistry);
        ReflectionTestUtils.setField(emailService, "fromAddress", "test@acaboumony.com");
    }

    private void allowRateLimit() {
        when(emailRateLimiter.isRateLimited(anyString())).thenReturn(false);
    }

    private void mockMimeMessage() {
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage(Session.getDefaultInstance(new Properties())));
    }

    @Nested
    class SendEmail {

        @Test
        void shouldSendEmailSuccessfully() {
            allowRateLimit();
            mockMimeMessage();
            when(templateEngine.process(anyString(), any())).thenReturn("<html>content</html>");

            emailService.sendEmail("test@test.com", "Subject", "welcome", Map.of("name", "Test"), "corr-1");

            verify(mailSender).send(any(MimeMessage.class));
            verify(notificationLogRepository).save(any(NotificationLog.class));
        }

        @Test
        void shouldSkipDuplicateCorrelationId() {
            when(notificationLogRepository.findByCorrelationIdAndEventType("corr-1", "welcome"))
                    .thenReturn(Optional.of(new NotificationLog()));

            emailService.sendEmail("test@test.com", "Subject", "welcome", Map.of(), "corr-1");

            verify(mailSender, never()).send(any(MimeMessage.class));
            verify(notificationLogRepository, never()).save(any());
        }

        @Test
        void shouldRetryOnFailure() {
            allowRateLimit();
            mockMimeMessage();
            when(templateEngine.process(anyString(), any())).thenReturn("<html>content</html>");
            doThrow(new MailSendException("SMTP unavailable"))
                    .doThrow(new MailSendException("SMTP unavailable"))
                    .doNothing()
                    .when(mailSender).send(any(MimeMessage.class));

            emailService.sendEmail("test@test.com", "Subject", "welcome", Map.of(), "corr-1");

            verify(mailSender, times(3)).send(any(MimeMessage.class));
            verify(notificationLogRepository).save(any(NotificationLog.class));
        }

        @Test
        void shouldThrowAfterMaxRetries() {
            allowRateLimit();
            mockMimeMessage();
            when(templateEngine.process(anyString(), any())).thenReturn("<html>content</html>");
            doThrow(new MailSendException("SMTP unavailable"))
                    .when(mailSender).send(any(MimeMessage.class));

            assertThatThrownBy(() ->
                    emailService.sendEmail("test@test.com", "Subject", "welcome", Map.of(), "corr-1"))
                    .isInstanceOf(EmailDeliveryException.class);

            verify(notificationLogRepository).save(any(NotificationLog.class));
        }
    }

    @Nested
    class Idempotency {

        @Test
        void shouldNotSendDuplicateEmail() {
            when(notificationLogRepository.findByCorrelationIdAndEventType("dup-key", "welcome"))
                    .thenReturn(Optional.of(new NotificationLog(
                            UUID.randomUUID(), "welcome", "test@test.com",
                            "SENT", "dup-key", null, null
                    )));

            emailService.sendEmail("test@test.com", "Subject", "welcome", Map.of(), "dup-key");

            verify(mailSender, never()).send(any(MimeMessage.class));
        }

        @Test
        void shouldSendWhenNoCorrelationId() {
            allowRateLimit();
            mockMimeMessage();
            when(templateEngine.process(anyString(), any())).thenReturn("<html>content</html>");

            emailService.sendEmail("test@test.com", "Subject", "welcome", Map.of(), null);

            verify(mailSender).send(any(MimeMessage.class));
        }
    }

    @Nested
    class RateLimit {

        @Test
        void shouldSkipWhenRateLimited() {
            when(emailRateLimiter.isRateLimited("test@test.com")).thenReturn(true);

            emailService.sendEmail("test@test.com", "Subject", "welcome", Map.of(), "corr-1");

            verify(mailSender, never()).send(any(MimeMessage.class));
            verify(notificationLogRepository, never()).save(any());
        }
    }

    @Nested
    class TemplateFallback {

        @Test
        void shouldFallbackToPlainTextWhenTemplateFails() {
            allowRateLimit();
            mockMimeMessage();
            when(templateEngine.process(anyString(), any())).thenThrow(new RuntimeException("Template error"));

            emailService.sendEmail("test@test.com", "Subject", "broken-template", Map.of("key", "value"), "corr-1");

            verify(mailSender).send(any(MimeMessage.class));
            verify(notificationLogRepository).save(any(NotificationLog.class));
        }
    }
}
