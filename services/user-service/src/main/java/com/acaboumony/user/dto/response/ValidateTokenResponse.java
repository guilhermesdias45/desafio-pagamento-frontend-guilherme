package com.acaboumony.user.dto.response;

import java.util.UUID;

/**
 * Response body for {@code POST /internal/auth/validate-token}.
 *
 * @param userId     authenticated user's UUID
 * @param email      authenticated user's email
 * @param role       role name string (e.g. {@code "CUSTOMER"})
 * @param merchantId merchant UUID — {@code null} for CUSTOMER/STAFF roles
 */
public record ValidateTokenResponse(
        UUID userId,
        String email,
        String role,
        UUID merchantId
) {}
