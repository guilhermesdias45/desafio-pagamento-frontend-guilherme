package com.acaboumony.user.dto.response;

import java.util.UUID;

/**
 * Response body for {@code POST /api/v1/auth/register}.
 *
 * @param userId         new user's UUID
 * @param email          registered email
 * @param role           role name string
 * @param merchantId     merchant UUID for MERCHANT_OWNER, {@code null} for CUSTOMER
 * @param emailConfirmed always {@code false} at registration — email must be confirmed first
 */
public record RegisterResponse(
        UUID userId,
        String email,
        String role,
        UUID merchantId,
        boolean emailConfirmed
) {}
