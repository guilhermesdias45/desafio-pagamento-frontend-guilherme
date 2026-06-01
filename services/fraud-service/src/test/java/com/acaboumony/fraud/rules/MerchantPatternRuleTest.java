package com.acaboumony.fraud.rules;

import com.acaboumony.fraud.dto.request.FraudAnalysisRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import static org.mockito.quality.Strictness.LENIENT;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = LENIENT)
class MerchantPatternRuleTest {

    @Mock
    private StringRedisTemplate redis;
    @Mock
    private ZSetOperations<String, String> zSetOps;

    private MerchantPatternRule rule;
    private FraudAnalysisRequest request;

    @BeforeEach
    void setUp() {
        lenient().when(redis.opsForZSet()).thenReturn(zSetOps);
        rule = new MerchantPatternRule();
        request = new FraudAnalysisRequest(
            "txn_001", UUID.randomUUID(), UUID.randomUUID(), 5000L,
            "visa", "192.168.1.1", null, null, null
        );
    }

    @Test
    void shouldReturnZeroWhenNoTransactions() {
        when(zSetOps.count(anyString(), anyDouble(), anyDouble())).thenReturn(0L);
        assertEquals(0, rule.evaluate(request, redis));
    }

    @Test
    void shouldReturnZeroWhenFewerThanThreeTransactions() {
        when(zSetOps.count(anyString(), anyDouble(), anyDouble())).thenReturn(2L);
        assertEquals(0, rule.evaluate(request, redis));
    }

    @Test
    void shouldReturnTenWhenExactlyThreeTransactions() {
        when(zSetOps.count(anyString(), anyDouble(), anyDouble())).thenReturn(3L);
        assertEquals(10, rule.evaluate(request, redis));
    }

    @Test
    void shouldReturnTenWhenFourTransactions() {
        when(zSetOps.count(anyString(), anyDouble(), anyDouble())).thenReturn(4L);
        assertEquals(10, rule.evaluate(request, redis));
    }

    @Test
    void shouldReturnTwentyWhenExactlyFiveTransactions() {
        when(zSetOps.count(anyString(), anyDouble(), anyDouble())).thenReturn(5L);
        assertEquals(20, rule.evaluate(request, redis));
    }

    @Test
    void shouldReturnTwentyWhenMoreThanFiveTransactions() {
        when(zSetOps.count(anyString(), anyDouble(), anyDouble())).thenReturn(10L);
        assertEquals(20, rule.evaluate(request, redis));
    }

    @Test
    void shouldUseMerchantSpecificRedisKey() {
        when(zSetOps.count(anyString(), anyDouble(), anyDouble())).thenReturn(3L);
        rule.evaluate(request, redis);
        verify(zSetOps).count(eq("fraud:merchant_velocity:" + request.merchantId()), anyDouble(), anyDouble());
    }

    @Test
    void shouldReturnCorrectReason() {
        assertEquals("MERCHANT_PATTERN", rule.getReason());
    }

    @Test
    void shouldReturnCorrectScore() {
        assertEquals(20, rule.getScore());
    }
}
