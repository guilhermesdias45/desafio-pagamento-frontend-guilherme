package com.acaboumony.notification.dto.event;

import java.time.Instant;
import java.util.UUID;

public record User2faEnabledEvent(
        UUID userId,
        String email,
        String fullName,
        Instant enabledAt
) {}
