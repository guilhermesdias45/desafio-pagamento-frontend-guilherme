package com.acaboumony.payment.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RateLimitServiceTest {

    @Mock StringRedisTemplate redisTemplate;
    @Mock ValueOperations<String, String> valueOps;

    RateLimitService rateLimitService;

    private static final UUID CUSTOMER_ID = UUID.randomUUID();
    private static final String KEY = "pay:ratelimit:" + CUSTOMER_ID;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        rateLimitService = new RateLimitService(redisTemplate, 100);
    }

    @Test
    void isAllowed_returns_true_when_first_request() {
        when(valueOps.increment(KEY)).thenReturn(1L);
        assertThat(rateLimitService.isAllowed(CUSTOMER_ID)).isTrue();
        // First request sets expiry
        verify(redisTemplate).expire(eq(KEY), any());
    }

    @Test
    void isAllowed_returns_true_when_within_limit() {
        when(valueOps.increment(KEY)).thenReturn(50L);
        assertThat(rateLimitService.isAllowed(CUSTOMER_ID)).isTrue();
    }

    @Test
    void isAllowed_returns_true_at_exact_limit() {
        when(valueOps.increment(KEY)).thenReturn(100L);
        assertThat(rateLimitService.isAllowed(CUSTOMER_ID)).isTrue();
    }

    @Test
    void isAllowed_returns_false_when_exceeded() {
        when(valueOps.increment(KEY)).thenReturn(101L);
        assertThat(rateLimitService.isAllowed(CUSTOMER_ID)).isFalse();
    }

    @Test
    void isAllowed_returns_true_when_redis_returns_null() {
        when(valueOps.increment(KEY)).thenReturn(null);
        assertThat(rateLimitService.isAllowed(CUSTOMER_ID)).isTrue();
    }
}
