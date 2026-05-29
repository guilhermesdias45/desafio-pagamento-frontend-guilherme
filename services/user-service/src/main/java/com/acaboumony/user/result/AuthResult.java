package com.acaboumony.user.result;

import java.time.Instant;

/**
 * Sealed interface representing the outcome of an authentication attempt.
 *
 * <p>Three permitted results:</p>
 * <ul>
 *   <li>{@link Success} — credentials valid, tokens issued.</li>
 *   <li>{@link RequiresTwoFactor} — credentials valid but 2FA code still needed.</li>
 *   <li>{@link Failure} — authentication failed (bad credentials, locked, etc.).</li>
 * </ul>
 */
public sealed interface AuthResult
        permits AuthResult.Success, AuthResult.RequiresTwoFactor, AuthResult.Failure {

    /**
     * Successful authentication — access and refresh tokens are available.
     *
     * @param accessToken       signed JWT (Bearer)
     * @param tokenType         always {@code "Bearer"}
     * @param expiresIn         token TTL in seconds (900)
     * @param requiresTwoFactor always {@code false} — included for wire compatibility
     * @param refreshToken      opaque UUID — passed to controller to set HttpOnly cookie;
     *                          NEVER serialised into the response body
     */
    record Success(
            String accessToken,
            String tokenType,
            int expiresIn,
            boolean requiresTwoFactor,
            String refreshToken
    ) implements AuthResult {}

    /**
     * Credentials valid but 2FA code required to complete authentication.
     *
     * @param requiresTwoFactor always {@code true}
     * @param twoFactorToken    short-lived opaque token stored in Redis ({@code 2fa_login:*}, TTL 5 min)
     */
    record RequiresTwoFactor(
            boolean requiresTwoFactor,
            String twoFactorToken
    ) implements AuthResult {}

    /**
     * Authentication failed.
     *
     * @param errorCode  machine-readable error code
     * @param message    human-readable message
     * @param retryable  whether the client can retry with the same input
     * @param unlockAt   for {@code ACCOUNT_LOCKED} — when the lock expires; {@code null} otherwise
     */
    record Failure(
            String errorCode,
            String message,
            boolean retryable,
            Instant unlockAt
    ) implements AuthResult {}
}
