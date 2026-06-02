package com.acaboumony.payment.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

/**
 * Sliding window rate limiter using Redis counters.
 * Key format: "pay:ratelimit:{customerId}", TTL 60s.
 * Allows up to maxRequestsPerMinute requests per customer per minute.
 */
@Service
public class RateLimitService {

    private static final Logger log = LoggerFactory.getLogger(RateLimitService.class);
    private static final String KEY_PREFIX = "pay:ratelimit:";
    private static final Duration WINDOW = Duration.ofSeconds(60);

    private final StringRedisTemplate redisTemplate;
    private final int maxRequestsPerMinute;

    public RateLimitService(
            StringRedisTemplate redisTemplate,
            @Value("${payment.rate-limit.max-requests-per-minute:100}") int maxRequestsPerMinute
    ) {
        this.redisTemplate = redisTemplate;
        this.maxRequestsPerMinute = maxRequestsPerMinute;
    }

    /**
     * Checks and increments the rate limit counter for the given customer.
     *
     * @param customerId the customer UUID
     * @return true if the request is allowed (under limit), false if exceeded
     */
    public boolean isAllowed(UUID customerId) {
        String key = KEY_PREFIX + customerId;
        Long count = redisTemplate.opsForValue().increment(key);

        if (count == null) {
            log.warn("Null count from Redis rate limit for customerId={}, allowing request", customerId);
            return true;
        }

        if (count == 1) {
            // First request in window — set expiry
            redisTemplate.expire(key, WINDOW);
        }

        boolean allowed = count <= maxRequestsPerMinute;
        if (!allowed) {
            log.warn("Rate limit exceeded for customerId={}: count={}", customerId, count);
        }
        return allowed;
    }
}
