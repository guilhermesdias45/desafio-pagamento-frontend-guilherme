package com.acaboumony.user.service;

import com.acaboumony.user.config.SecurityLoginProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

/**
 * Manages login attempt counters and account lockouts in Redis.
 *
 * <p>Redis keys used:</p>
 * <ul>
 *   <li>{@code login_attempts:{email}} — integer counter, TTL = lockout duration.</li>
 *   <li>{@code account_locked:{email}} — ISO-8601 {@code unlockAt} string, TTL = lockout duration.</li>
 * </ul>
 *
 * <p>Rate limiting is by email address (not by IP) per {@code plan.md}.</p>
 *
 * <p>Email addresses are never logged in plaintext — a SHA-256 hash prefix is used.</p>
 */
@Service
public class LoginAttemptService {

    private static final Logger log = LoggerFactory.getLogger(LoginAttemptService.class);

    private static final String ATTEMPTS_KEY = "login_attempts:%s";
    private static final String LOCKED_KEY   = "account_locked:%s";

    private final StringRedisTemplate redis;
    private final SecurityLoginProperties props;

    public LoginAttemptService(StringRedisTemplate redis, SecurityLoginProperties props) {
        this.redis = redis;
        this.props = props;
    }

    /**
     * Records a failed login attempt for the given email.
     *
     * @param email the email that failed authentication
     * @return result containing the current attempt count, whether the account is now locked,
     *         and the unlock time (if just locked)
     */
    public LoginAttemptResult recordFailure(String email) {
        String attemptsKey = attemptsKey(email);
        String lockedKey   = lockedKey(email);
        Duration lockDuration = Duration.ofMinutes(props.lockoutDurationMinutes());

        Long count = redis.opsForValue().increment(attemptsKey);
        if (count == null) count = 1L;

        // Set TTL on first attempt
        if (count == 1) {
            redis.expire(attemptsKey, lockDuration);
        }

        boolean nowLocked = false;
        Instant unlockAt = null;

        if (count >= props.maxAttempts()) {
            unlockAt = Instant.now().plus(lockDuration);
            redis.opsForValue().set(lockedKey, unlockAt.toString(), lockDuration);
            nowLocked = true;
            log.info("Account locked: emailHash={}, attempts={}", hashEmail(email), count);
        }

        return new LoginAttemptResult(count.intValue(), nowLocked, unlockAt);
    }

    /**
     * Clears all failure state for the given email (called on successful login).
     */
    public void recordSuccess(String email) {
        redis.delete(attemptsKey(email));
        redis.delete(lockedKey(email));
    }

    /**
     * @return {@code true} if the account is currently locked
     */
    public boolean isLocked(String email) {
        return Boolean.TRUE.equals(redis.hasKey(lockedKey(email)));
    }

    /**
     * @return the time the lock expires, or empty if not locked
     */
    public Optional<Instant> getUnlockAt(String email) {
        String value = redis.opsForValue().get(lockedKey(email));
        if (value == null) return Optional.empty();
        try {
            return Optional.of(Instant.parse(value));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    // ─── private helpers ─────────────────────────────────────────────────────

    private String attemptsKey(String email) {
        return String.format(ATTEMPTS_KEY, email);
    }

    private String lockedKey(String email) {
        return String.format(LOCKED_KEY, email);
    }

    private String hashEmail(String email) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(email.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash).substring(0, 12) + "...";
        } catch (NoSuchAlgorithmException e) {
            return "[hash-error]";
        }
    }

    /**
     * Immutable result returned by {@link #recordFailure(String)}.
     */
    public record LoginAttemptResult(int attempts, boolean nowLocked, Instant unlockAt) {}
}
