package com.acaboumony.user.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Manages opaque refresh tokens stored in Redis.
 *
 * <p>Two Redis keys are maintained per token for O(1) lookup:</p>
 * <ul>
 *   <li>{@code refresh_token:{userId}:{tokenId}} → {@code userId} (TTL 7 days)</li>
 *   <li>{@code refresh_token_lookup:{tokenId}} → {@code userId} (TTL 7 days)</li>
 * </ul>
 *
 * <p>The lookup key enables {@link #validateAndDelete(String)} in O(1) instead of
 * O(n) {@code KEYS} scan.</p>
 *
 * <p>Token rotation is enforced by {@link #validateAndDelete(String)} — the token
 * is atomically removed before the new one is issued. Any attempt to reuse a
 * rotated token returns empty.</p>
 */
@Service
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);
    private static final Duration TTL = Duration.ofDays(7);

    private final StringRedisTemplate redis;

    public RefreshTokenService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     * Issues a new refresh token for the given user.
     *
     * @param userId owner of the token
     * @return opaque UUID refresh token string
     */
    public String issue(UUID userId) {
        String token = UUID.randomUUID().toString();
        String primaryKey = primaryKey(userId, token);
        String lookupKey  = lookupKey(token);

        redis.opsForValue().set(primaryKey, userId.toString(), TTL);
        redis.opsForValue().set(lookupKey,  userId.toString(), TTL);

        log.debug("Refresh token issued: userId={}", userId);
        return token;
    }

    /**
     * Validates the token (by looking it up in Redis) and atomically deletes it.
     *
     * <p>If the token was already rotated (deleted by a previous call), this returns empty —
     * preventing replay attacks.</p>
     *
     * @param token opaque refresh token
     * @return the associated userId, or empty if the token is unknown/already used
     */
    public Optional<UUID> validateAndDelete(String token) {
        String lookupKey = lookupKey(token);
        String userIdStr = redis.opsForValue().get(lookupKey);

        if (userIdStr == null) {
            log.debug("Refresh token not found or already rotated");
            return Optional.empty();
        }

        UUID userId = UUID.fromString(userIdStr);

        // Atomically delete both keys (rotation invariant)
        redis.delete(lookupKey);
        redis.delete(primaryKey(userId, token));

        return Optional.of(userId);
    }

    /**
     * Revokes (deletes) a specific refresh token.
     * Idempotent — no-op if already deleted.
     *
     * @param token opaque refresh token to revoke
     */
    public void revoke(String token) {
        String lookupKey = lookupKey(token);
        String userIdStr = redis.opsForValue().get(lookupKey);

        if (userIdStr != null) {
            UUID userId = UUID.fromString(userIdStr);
            redis.delete(lookupKey);
            redis.delete(primaryKey(userId, token));
            log.debug("Refresh token revoked: userId={}", userId);
        }
        // If already gone — idempotent, no-op
    }

    private String primaryKey(UUID userId, String token) {
        return "refresh_token:" + userId + ":" + token;
    }

    private String lookupKey(String token) {
        return "refresh_token_lookup:" + token;
    }
}
