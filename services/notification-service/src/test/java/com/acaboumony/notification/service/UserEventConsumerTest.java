package com.acaboumony.notification.service;

import com.acaboumony.notification.consumer.UserEventConsumer;
import com.acaboumony.notification.dto.event.User2faEnabledEvent;
import com.acaboumony.notification.dto.event.UserLoginBlockedEvent;
import com.acaboumony.notification.dto.event.UserRegisteredEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserEventConsumerTest {

    @Mock
    private EmailService emailService;

    private UserEventConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new UserEventConsumer(emailService);
    }

    @Test
    void shouldSendWelcomeEmailOnUserRegistered() {
        var event = new UserRegisteredEvent(
                UUID.randomUUID(), "test@test.com", "Test User",
                "MERCHANT", "token-123", Instant.now()
        );

        consumer.consumeUserRegistered(event);

        verify(emailService).sendEmail(
                eq("test@test.com"),
                eq("Bem-vindo(a) à Acabou o Mony! Confirme seu email"),
                eq("welcome"),
                any(),
                anyString()
        );
    }

    @Test
    void shouldSendBlockedEmailOnLoginBlocked() {
        var event = new UserLoginBlockedEvent(
                UUID.randomUUID(), "test@test.com",
                Instant.now(), Instant.now().plusSeconds(1800),
                "192.168.1.1", 5
        );

        consumer.consumeUserLoginBlocked(event);

        verify(emailService).sendEmail(
                eq("test@test.com"),
                eq("Acesso à sua conta bloqueado temporariamente"),
                eq("login-blocked"),
                any(),
                anyString()
        );
    }

    @Test
    void shouldSend2faEnabledEmail() {
        var event = new User2faEnabledEvent(
                UUID.randomUUID(), "test@test.com", "Test User", Instant.now()
        );

        consumer.consumeUser2faEnabled(event);

        verify(emailService).sendEmail(
                eq("test@test.com"),
                eq("Autenticação de dois fatores ativada"),
                eq("2fa-enabled"),
                any(),
                anyString()
        );
    }
}
