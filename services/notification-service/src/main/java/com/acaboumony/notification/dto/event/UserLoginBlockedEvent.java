package com.acaboumony.notification.dto.event;

import java.time.Instant;
import java.util.UUID;

public record UserLoginBlockedEvent(
        UUID userId,
        String email,
        Instant blockedAt,
        Instant unlockAt,
        String ipAddress,
        int attemptCount
) {}
