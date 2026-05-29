package com.acaboumony.user.event.payload;

import com.acaboumony.user.event.UserEvent;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

/**
 * Published when an account is locked due to too many failed login attempts.
 * Topic: {@code user-events}. Key: {@code userId.toString()} (may be a null-replacement UUID
 * if the email does not correspond to a known user).
 */
public record UserLoginBlockedEvent(
        UUID userId,
        String email,
        Instant unlockAt,
        Instant occurredAt
) implements UserEvent {

    @Override
    @JsonProperty("eventType")
    public String eventType() {
        return "user.login.blocked";
    }
}
