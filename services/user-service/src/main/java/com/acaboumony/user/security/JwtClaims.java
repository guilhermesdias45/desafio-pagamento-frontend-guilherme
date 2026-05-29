package com.acaboumony.user.security;

import com.acaboumony.user.domain.enums.UserRole;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Immutable representation of the claims extracted from a validated JWT access token.
 *
 * @param sub        user ID (JWT {@code sub} claim)
 * @param email      user email
 * @param role       user role
 * @param merchantId merchant ID — {@code null} for CUSTOMER and STAFF roles
 * @param issuedAt   token issue time
 * @param expiresAt  token expiration time
 */
public record JwtClaims(
        UUID sub,
        String email,
        UserRole role,
        UUID merchantId,
        Instant issuedAt,
        Instant expiresAt
) {

    /**
     * Factory method that parses a {@link Map} of raw JWT claims into a {@link JwtClaims} record.
     *
     * @param claims raw claims map from JJWT
     * @param sub    subject string from {@code Jws.getPayload().getSubject()}
     * @param iat    issued-at from {@code Jws.getPayload().getIssuedAt()}
     * @param exp    expiration from {@code Jws.getPayload().getExpiration()}
     * @return parsed {@link JwtClaims}
     * @throws JwtValidationException if mandatory claims are missing
     */
    public static JwtClaims fromMap(
            Map<String, Object> claims,
            String sub,
            Instant iat,
            Instant exp) {

        String email = (String) claims.get("email");
        String roleStr = (String) claims.get("role");

        if (email == null || email.isBlank()) {
            throw new JwtValidationException("MISSING_CLAIMS", "JWT missing 'email' claim");
        }
        if (roleStr == null || roleStr.isBlank()) {
            throw new JwtValidationException("MISSING_CLAIMS", "JWT missing 'role' claim");
        }

        UserRole role;
        try {
            role = UserRole.valueOf(roleStr);
        } catch (IllegalArgumentException e) {
            throw new JwtValidationException("INVALID_CLAIMS", "JWT 'role' claim has unknown value: " + roleStr);
        }

        String merchantIdStr = (String) claims.get("merchantId");
        UUID merchantId = (merchantIdStr != null && !merchantIdStr.isEmpty())
                ? UUID.fromString(merchantIdStr)
                : null;

        return new JwtClaims(
                UUID.fromString(sub),
                email,
                role,
                merchantId,
                iat,
                exp
        );
    }
}
