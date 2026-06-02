package com.acaboumony.fraud.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

/**
 * Redis-backed service for velocity tracking and IP blacklist management.
 *
 * <p>Uses a sliding counter per customer to detect rapid transaction sequences,
 * and a simple key-presence blacklist for known malicious IPs.</p>
 *
 * <p>Keys used:</p>
 * <ul>
 *   <li>{@code fraud:velocity:{customerId}} — transaction count, TTL 5 minutes</li>
 *   <li>{@code fraud:blacklist:{ip}} — presence flag, configurable TTL</li>
 * </ul>
 */
@Service
public class VelocityTrackingService {

    private static final Logger log = LoggerFactory.getLogger(VelocityTrackingService.class);

    private static final String VELOCITY_KEY_PREFIX  = "fraud:velocity:";
    private static final String BLACKLIST_KEY_PREFIX = "fraud:blacklist:";
    private static final Duration VELOCITY_WINDOW    = Duration.ofMinutes(5);

    private final StringRedisTemplate redisTemplate;

    public VelocityTrackingService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Increments the velocity counter for the given customer and resets its TTL to 5 minutes.
     */
    public void incrementVelocityCounter(UUID customerId) {
        String key = VELOCITY_KEY_PREFIX + customerId;
        redisTemplate.opsForValue().increment(key);
        redisTemplate.expire(key, VELOCITY_WINDOW);
        log.debug("Incremented velocity counter for customer {}", customerId);
    }

    /**
     * Returns the current transaction count for the given customer in the last 5 minutes.
     */
    public long getVelocityCount(UUID customerId) {
        String key = VELOCITY_KEY_PREFIX + customerId;
        String value = redisTemplate.opsForValue().get(key);
        return value != null ? Long.parseLong(value) : 0L;
    }

    /**
     * Alias for {@link #getVelocityCount(UUID)}.
     */
    public long getTransactionCount(UUID customerId) {
        return getVelocityCount(customerId);
    }

    /**
     * Returns {@code true} if the given IP address is in the blacklist.
     */
    public boolean isIpBlacklisted(String ipAddress) {
        String key = BLACKLIST_KEY_PREFIX + ipAddress;
        Boolean exists = redisTemplate.hasKey(key);
        return Boolean.TRUE.equals(exists);
    }

    /**
     * Adds an IP address to the blacklist for the specified duration.
     */
    public void addToBlacklist(String ipAddress, Duration duration) {
        String key = BLACKLIST_KEY_PREFIX + ipAddress;
        redisTemplate.opsForValue().set(key, "1", duration);
        // Log only first 3 octets to avoid storing full IP in logs (PCI DSS)
        log.warn("IP blacklisted for {}h: {}.xxx",
                duration.toHours(), maskIp(ipAddress));
    }

    /**
     * Masks the last octet of an IPv4 address for safe logging.
     */
    private String maskIp(String ip) {
        if (ip == null) return "null";
        int lastDot = ip.lastIndexOf('.');
        return lastDot >= 0 ? ip.substring(0, lastDot) + ".xxx" : ip.substring(0, Math.min(ip.length(), 9)) + ".xxx";
    }
}
