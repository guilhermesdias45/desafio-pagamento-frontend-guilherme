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
class VelocityRuleTest {

    @Mock
    private StringRedisTemplate redis;
    @Mock
    private ZSetOperations<String, String> zSetOps;

    private VelocityRule rule;
    private FraudAnalysisRequest request;

    @BeforeEach
    void setUp() {
        lenient().when(redis.opsForZSet()).thenReturn(zSetOps);
        rule = new VelocityRule();
        request = new FraudAnalysisRequest(
            "txn_001", UUID.randomUUID(), UUID.randomUUID(), 5000L,
            "visa", "192.168.1.1", null, null, null
        );
    }

    @Test
    void shouldReturnZeroWhenFewerThanThreeTransactions() {
        when(zSetOps.count(anyString(), anyDouble(), anyDouble())).thenReturn(2L);
        assertEquals(0, rule.evaluate(request, redis));
    }

    @Test
    void shouldReturnThirtyWhenThreeOrMoreTransactions() {
        when(zSetOps.count(anyString(), anyDouble(), anyDouble())).thenReturn(3L);
        assertEquals(30, rule.evaluate(request, redis));
    }

    @Test
    void shouldReturnThirtyWhenManyTransactions() {
        when(zSetOps.count(anyString(), anyDouble(), anyDouble())).thenReturn(10L);
        assertEquals(30, rule.evaluate(request, redis));
    }

    @Test
    void shouldReturnZeroWhenNoTransactions() {
        when(zSetOps.count(anyString(), anyDouble(), anyDouble())).thenReturn(0L);
        assertEquals(0, rule.evaluate(request, redis));
    }

    @Test
    void shouldUseCustomerSpecificRedisKey() {
        when(zSetOps.count(anyString(), anyDouble(), anyDouble())).thenReturn(3L);
        rule.evaluate(request, redis);
        verify(zSetOps).count(eq("fraud:velocity:" + request.customerId()), anyDouble(), anyDouble());
    }

    @Test
    void shouldReturnCorrectReasonAndScore() {
        assertEquals("VELOCITY_EXCEEDED", rule.getReason());
        assertEquals(30, rule.getScore());
    }
}
