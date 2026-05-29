package com.acaboumony.notification.dto.event;

import java.time.Instant;
import java.util.UUID;

public record UserRegisteredEvent(
        UUID userId,
        String email,
        String fullName,
        String role,
        String confirmationToken,
        Instant registeredAt
) {}
