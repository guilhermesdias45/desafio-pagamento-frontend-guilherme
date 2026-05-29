package com.acaboumony.user.event.payload;

import com.acaboumony.user.event.UserEvent;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

/**
 * Published when a user successfully activates two-factor authentication (2FA).
 * Topic: {@code user-events}. Key: {@code userId.toString()}.
 */
public record UserTwoFactorEnabledEvent(
        UUID userId,
        Instant occurredAt
) implements UserEvent {

    @Override
    @JsonProperty("eventType")
    public String eventType() {
        return "user.2fa.enabled";
    }
}
