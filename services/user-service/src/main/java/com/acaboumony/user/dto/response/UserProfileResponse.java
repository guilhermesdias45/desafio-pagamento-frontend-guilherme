package com.acaboumony.user.dto.response;

import java.time.Instant;
import java.util.UUID;

/**
 * Response body for {@code GET /api/v1/users/me} and {@code PATCH /api/v1/users/me}.
 * Never includes passwordHash or totpSecretEncrypted.
 */
public record UserProfileResponse(
        UUID userId,
        String email,
        String fullName,
        String role,
        UUID merchantId,
        boolean twoFactorEnabled,
        Instant createdAt
) {}
