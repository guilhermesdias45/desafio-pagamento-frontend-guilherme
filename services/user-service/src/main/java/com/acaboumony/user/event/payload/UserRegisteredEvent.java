package com.acaboumony.user.event.payload;

import com.acaboumony.user.domain.enums.UserRole;
import com.acaboumony.user.event.UserEvent;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

/**
 * Published when a new user is successfully registered.
 * Topic: {@code user-events}. Key: {@code userId.toString()}.
 */
public record UserRegisteredEvent(
        UUID userId,
        String email,
        UserRole role,
        UUID merchantId,
        Instant occurredAt
) implements UserEvent {

    @Override
    @JsonProperty("eventType")
    public String eventType() {
        return "user.registered";
    }
}
