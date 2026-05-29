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
import org.springframework.data.redis.core.ValueOperations;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = LENIENT)
class FirstPurchaseRuleTest {

    @Mock
    private StringRedisTemplate redis;
    @Mock
    private ValueOperations<String, String> valueOps;

    private FirstPurchaseRule rule;

    @BeforeEach
    void setUp() {
        lenient().when(redis.opsForValue()).thenReturn(valueOps);
        rule = new FirstPurchaseRule();
    }

    @Test
    void shouldReturnZeroWhenCustomerHasHistoryAndBelowMax() {
        when(valueOps.get(anyString())).thenReturn("5");
        var request = new FraudAnalysisRequest(
            "txn_001", UUID.randomUUID(), UUID.randomUUID(), 50000L,
            "visa", "192.168.1.1", null, null, null
        );
        assertEquals(0, rule.evaluate(request, redis));
    }

    @Test
    void shouldReturnZeroWhenFirstPurchaseButLowValue() {
        when(valueOps.get(anyString())).thenReturn(null);
        var request = new FraudAnalysisRequest(
            "txn_001", UUID.randomUUID(), UUID.randomUUID(), 10000L,
            "visa", "192.168.1.1", null, null, null
        );
        assertEquals(0, rule.evaluate(request, redis));
    }

    @Test
    void shouldReturnTwentyWhenFirstPurchaseAndMaxValue() {
        when(valueOps.get(anyString())).thenReturn(null);
        var request = new FraudAnalysisRequest(
            "txn_001", UUID.randomUUID(), UUID.randomUUID(), 99999L,
            "visa", "192.168.1.1", null, null, null
        );
        assertEquals(20, rule.evaluate(request, redis));
    }

    @Test
    void shouldReturnTwentyWhenFirstPurchaseAndAboveMaxValue() {
        when(valueOps.get(anyString())).thenReturn(null);
        var request = new FraudAnalysisRequest(
            "txn_001", UUID.randomUUID(), UUID.randomUUID(), 200000L,
            "visa", "192.168.1.1", null, null, null
        );
        assertEquals(20, rule.evaluate(request, redis));
    }

    @Test
    void shouldReturnZeroWhenHasHistoryAndMaxValue() {
        when(valueOps.get(anyString())).thenReturn("1");
        var request = new FraudAnalysisRequest(
            "txn_001", UUID.randomUUID(), UUID.randomUUID(), 99999L,
            "visa", "192.168.1.1", null, null, null
        );
        assertEquals(0, rule.evaluate(request, redis));
    }

    @Test
    void shouldReturnCorrectReasonAndScore() {
        assertEquals("FIRST_PURCHASE_MAX_VALUE", rule.getReason());
        assertEquals(20, rule.getScore());
    }
}
