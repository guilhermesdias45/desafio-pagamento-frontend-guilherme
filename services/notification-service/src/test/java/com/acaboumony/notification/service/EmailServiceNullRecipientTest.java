package com.acaboumony.notification.service;

import com.acaboumony.notification.repository.NotificationLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.thymeleaf.spring6.SpringTemplateEngine;

import jakarta.mail.internet.MimeMessage;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EmailServiceNullRecipientTest {

    @Mock
    private JavaMailSender mailSender;
    @Mock
    private SpringTemplateEngine templateEngine;
    @Mock
    private NotificationLogRepository notificationLogRepository;

    private EmailService emailService;

    @BeforeEach
    void setUp() {
        emailService = new EmailService(mailSender, templateEngine, notificationLogRepository);
    }

    @Test
    void shouldSkipNullRecipient() {
        emailService.sendEmail(null, "Subject", "template", Map.of(), "corr-id");
        verify(mailSender, never()).send(any(MimeMessage.class));
        verify(notificationLogRepository, never()).save(any());
    }

    @Test
    void shouldSkipBlankRecipient() {
        emailService.sendEmail("", "Subject", "template", Map.of(), "corr-id");
        verify(mailSender, never()).send(any(MimeMessage.class));
        verify(notificationLogRepository, never()).save(any());
    }
}
