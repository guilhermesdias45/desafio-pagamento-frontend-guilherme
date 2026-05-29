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
class IpChangeRuleTest {

    @Mock
    private StringRedisTemplate redis;
    @Mock
    private ValueOperations<String, String> valueOps;

    private IpChangeRule rule;

    @BeforeEach
    void setUp() {
        lenient().when(redis.opsForValue()).thenReturn(valueOps);
        rule = new IpChangeRule();
    }

    @Test
    void shouldReturnZeroWhenIpHasNotChanged() {
        when(valueOps.get(anyString())).thenReturn("192.168.1.1");
        var request = new FraudAnalysisRequest(
            "txn_001", UUID.randomUUID(), 5000L,
            "visa", "192.168.1.1", null, null, null
        );
        assertEquals(0, rule.evaluate(request, redis));
    }

    @Test
    void shouldReturnTwentyFiveWhenIpChangedAndRecentTimestamp() {
        long oneMinuteAgo = System.currentTimeMillis() - 30_000;
        when(valueOps.get(anyString()))
            .thenReturn("10.0.0.1")
            .thenReturn(String.valueOf(oneMinuteAgo));
        var request = new FraudAnalysisRequest(
            "txn_001", UUID.randomUUID(), 5000L,
            "visa", "192.168.1.1", null, null, null
        );
        assertEquals(25, rule.evaluate(request, redis));
    }

    @Test
    void shouldReturnZeroWhenIpChangedButOldTimestamp() {
        long fiveMinutesAgo = System.currentTimeMillis() - 300_000;
        when(valueOps.get(anyString()))
            .thenReturn("10.0.0.1")
            .thenReturn(String.valueOf(fiveMinutesAgo));
        var request = new FraudAnalysisRequest(
            "txn_001", UUID.randomUUID(), 5000L,
            "visa", "192.168.1.1", null, null, null
        );
        assertEquals(0, rule.evaluate(request, redis));
    }

    @Test
    void shouldReturnZeroWhenNoPreviousIpData() {
        when(valueOps.get(anyString())).thenReturn(null);
        var request = new FraudAnalysisRequest(
            "txn_001", UUID.randomUUID(), 5000L,
            "visa", "192.168.1.1", null, null, null
        );
        assertEquals(0, rule.evaluate(request, redis));
    }

    @Test
    void shouldReturnCorrectReasonAndScore() {
        assertEquals("IP_CHANGE_RAPID", rule.getReason());
        assertEquals(25, rule.getScore());
    }
}
