package com.acaboumony.user.event.payload;

import com.acaboumony.user.event.UserEvent;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

/**
 * Published when a user successfully authenticates.
 * Topic: {@code user-events}. Key: {@code userId.toString()}.
 */
public record UserLoginSuccessEvent(
        UUID userId,
        String email,
        String deviceFingerprint,
        Instant occurredAt
) implements UserEvent {

    @Override
    @JsonProperty("eventType")
    public String eventType() {
        return "user.login.success";
    }
}
