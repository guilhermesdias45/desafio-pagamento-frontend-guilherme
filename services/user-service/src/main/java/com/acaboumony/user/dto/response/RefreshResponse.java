package com.acaboumony.user.dto.response;

/**
 * Response body for {@code POST /api/v1/auth/refresh}.
 *
 * <p>{@code refreshToken} is passed to the controller to set an HttpOnly cookie —
 * it must NOT appear in the serialised JSON body sent to the client.</p>
 *
 * @param accessToken  new JWT access token
 * @param tokenType    always {@code "Bearer"}
 * @param expiresIn    token TTL in seconds (900)
 * @param refreshToken new opaque refresh token — for cookie only
 */
public record RefreshResponse(
        String accessToken,
        String tokenType,
        int expiresIn,
        String refreshToken
) {}
