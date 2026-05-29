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
class AmountAnomalyRuleTest {

    @Mock
    private StringRedisTemplate redis;
    @Mock
    private ValueOperations<String, String> valueOps;

    private AmountAnomalyRule rule;

    @BeforeEach
    void setUp() {
        lenient().when(redis.opsForValue()).thenReturn(valueOps);
        rule = new AmountAnomalyRule();
    }

    @Test
    void shouldReturnZeroWhenAmountBelowFiveTimesAverage() {
        when(valueOps.get(anyString())).thenReturn("10000");
        var request = new FraudAnalysisRequest(
            "txn_001", UUID.randomUUID(), UUID.randomUUID(), 30000L,
            "visa", "192.168.1.1", null, null, null
        );
        assertEquals(0, rule.evaluate(request, redis));
    }

    @Test
    void shouldReturnTwentyFiveWhenAmountIsFiveTimesAverage() {
        when(valueOps.get(anyString())).thenReturn("10000");
        var request = new FraudAnalysisRequest(
            "txn_001", UUID.randomUUID(), UUID.randomUUID(), 50000L,
            "visa", "192.168.1.1", null, null, null
        );
        assertEquals(25, rule.evaluate(request, redis));
    }

    @Test
    void shouldReturnTwentyFiveWhenAmountWellAboveFiveTimesAverage() {
        when(valueOps.get(anyString())).thenReturn("10000");
        var request = new FraudAnalysisRequest(
            "txn_001", UUID.randomUUID(), UUID.randomUUID(), 200000L,
            "visa", "192.168.1.1", null, null, null
        );
        assertEquals(25, rule.evaluate(request, redis));
    }

    @Test
    void shouldReturnZeroWhenNoHistoricalAverage() {
        when(valueOps.get(anyString())).thenReturn(null);
        var request = new FraudAnalysisRequest(
            "txn_001", UUID.randomUUID(), UUID.randomUUID(), 50000L,
            "visa", "192.168.1.1", null, null, null
        );
        assertEquals(0, rule.evaluate(request, redis));
    }

    @Test
    void shouldReturnCorrectReasonAndScore() {
        assertEquals("AMOUNT_ANOMALY", rule.getReason());
        assertEquals(25, rule.getScore());
    }
}
