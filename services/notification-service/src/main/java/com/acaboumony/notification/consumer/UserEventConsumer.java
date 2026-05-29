package com.acaboumony.notification.consumer;

import com.acaboumony.notification.dto.event.User2faEnabledEvent;
import com.acaboumony.notification.dto.event.UserLoginBlockedEvent;
import com.acaboumony.notification.dto.event.UserRegisteredEvent;
import com.acaboumony.notification.service.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class UserEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(UserEventConsumer.class);

    private final EmailService emailService;

    public UserEventConsumer(EmailService emailService) {
        this.emailService = emailService;
    }

    @KafkaListener(topics = "user.registered", groupId = "notification-service-group")
    public void consumeUserRegistered(UserRegisteredEvent event) {
        log.info("Received user.registered event for email={}", event.email());
        var variables = Map.<String, Object>of(
                "fullName", event.fullName(),
                "confirmationToken", event.confirmationToken(),
                "appBaseUrl", getAppBaseUrl()
        );
        emailService.sendEmail(
                event.email(),
                "Bem-vindo(a) à Acabou o Mony! Confirme seu email",
                "welcome",
                variables,
                event.userId().toString()
        );
    }

    @KafkaListener(topics = "user.login.blocked", groupId = "notification-service-group")
    public void consumeUserLoginBlocked(UserLoginBlockedEvent event) {
        log.info("Received user.login.blocked event for email={}", event.email());
        var variables = Map.<String, Object>of(
                "unlockAt", event.unlockAt().toString(),
                "email", event.email()
        );
        emailService.sendEmail(
                event.email(),
                "Acesso à sua conta bloqueado temporariamente",
                "login-blocked",
                variables,
                event.userId().toString()
        );
    }

    @KafkaListener(topics = "user.2fa.enabled", groupId = "notification-service-group")
    public void consumeUser2faEnabled(User2faEnabledEvent event) {
        log.info("Received user.2fa.enabled event for email={}", event.email());
        var variables = Map.<String, Object>of(
                "fullName", event.fullName()
        );
        emailService.sendEmail(
                event.email(),
                "Autenticação de dois fatores ativada",
                "2fa-enabled",
                variables,
                event.userId().toString()
        );
    }

    private static String getAppBaseUrl() {
        return System.getenv("APP_BASE_URL") != null
                ? System.getenv("APP_BASE_URL")
                : "https://app.acaboumony.com";
    }
}
