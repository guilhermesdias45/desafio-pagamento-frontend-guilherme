package com.acaboumony.fraud;

import com.acaboumony.fraud.service.VelocityTrackingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link VelocityTrackingService}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VelocityTrackingServiceTest {

    @Mock StringRedisTemplate redisTemplate;
    @Mock ValueOperations<String, String> valueOps;

    private VelocityTrackingService service;

    private static final UUID CUSTOMER_ID = UUID.fromString("12345678-1234-1234-1234-123456789012");

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        service = new VelocityTrackingService(redisTemplate);
    }

    @Test
    @DisplayName("incrementVelocityCounter — increments key and sets TTL")
    void incrementVelocityCounter_increments_and_sets_ttl() {
        service.incrementVelocityCounter(CUSTOMER_ID);

        verify(valueOps).increment("fraud:velocity:" + CUSTOMER_ID);
        verify(redisTemplate).expire(eq("fraud:velocity:" + CUSTOMER_ID), any(Duration.class));
    }

    @Test
    @DisplayName("getVelocityCount — returns parsed count from Redis")
    void getVelocityCount_returns_count() {
        when(valueOps.get("fraud:velocity:" + CUSTOMER_ID)).thenReturn("5");

        long count = service.getVelocityCount(CUSTOMER_ID);

        assertThat(count).isEqualTo(5L);
    }

    @Test
    @DisplayName("getVelocityCount — returns 0 when key does not exist")
    void getVelocityCount_returns_zero_when_absent() {
        when(valueOps.get("fraud:velocity:" + CUSTOMER_ID)).thenReturn(null);

        long count = service.getVelocityCount(CUSTOMER_ID);

        assertThat(count).isEqualTo(0L);
    }

    @Test
    @DisplayName("getTransactionCount — alias for getVelocityCount")
    void getTransactionCount_is_alias_for_getVelocityCount() {
        when(valueOps.get("fraud:velocity:" + CUSTOMER_ID)).thenReturn("3");

        long count = service.getTransactionCount(CUSTOMER_ID);

        assertThat(count).isEqualTo(3L);
    }

    @Test
    @DisplayName("isIpBlacklisted — returns true when key exists")
    void isIpBlacklisted_returns_true_when_key_exists() {
        when(redisTemplate.hasKey("fraud:blacklist:1.2.3.4")).thenReturn(Boolean.TRUE);

        assertThat(service.isIpBlacklisted("1.2.3.4")).isTrue();
    }

    @Test
    @DisplayName("isIpBlacklisted — returns false when key does not exist")
    void isIpBlacklisted_returns_false_when_key_absent() {
        when(redisTemplate.hasKey("fraud:blacklist:9.9.9.9")).thenReturn(Boolean.FALSE);

        assertThat(service.isIpBlacklisted("9.9.9.9")).isFalse();
    }

    @Test
    @DisplayName("isIpBlacklisted — returns false when hasKey returns null")
    void isIpBlacklisted_returns_false_when_null_from_redis() {
        when(redisTemplate.hasKey(anyString())).thenReturn(null);

        assertThat(service.isIpBlacklisted("1.1.1.1")).isFalse();
    }

    @Test
    @DisplayName("addToBlacklist — sets key with TTL")
    void addToBlacklist_sets_key_with_ttl() {
        service.addToBlacklist("5.5.5.5", Duration.ofHours(24));

        verify(valueOps).set(eq("fraud:blacklist:5.5.5.5"), eq("1"), eq(Duration.ofHours(24)));
    }
}
