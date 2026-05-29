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
class CardAbuseRuleTest {

    @Mock
    private StringRedisTemplate redis;
    @Mock
    private ValueOperations<String, String> valueOps;

    private CardAbuseRule rule;
    private FraudAnalysisRequest request;

    @BeforeEach
    void setUp() {
        lenient().when(redis.opsForValue()).thenReturn(valueOps);
        rule = new CardAbuseRule();
        request = new FraudAnalysisRequest(
            "txn_001", UUID.randomUUID(), 5000L,
            "visa", "192.168.1.1", null, null, null
        );
    }

    @Test
    void shouldReturnZeroWhenCardUsedInFewerThanThreeAccounts() {
        when(valueOps.get(anyString())).thenReturn("2");
        assertEquals(0, rule.evaluate(request, redis));
    }

    @Test
    void shouldReturnThirtyFiveWhenCardUsedInThreeAccounts() {
        when(valueOps.get(anyString())).thenReturn("3");
        assertEquals(35, rule.evaluate(request, redis));
    }

    @Test
    void shouldReturnThirtyFiveWhenCardUsedInManyAccounts() {
        when(valueOps.get(anyString())).thenReturn("10");
        assertEquals(35, rule.evaluate(request, redis));
    }

    @Test
    void shouldReturnZeroWhenNoCardUsageData() {
        when(valueOps.get(anyString())).thenReturn(null);
        assertEquals(0, rule.evaluate(request, redis));
    }

    @Test
    void shouldUsePaymentMethodBasedKey() {
        when(valueOps.get(anyString())).thenReturn("3");
        rule.evaluate(request, redis);
        verify(valueOps).get(eq("fraud:card:visa"));
    }

    @Test
    void shouldReturnCorrectReasonAndScore() {
        assertEquals("CARD_ABUSE", rule.getReason());
        assertEquals(35, rule.getScore());
    }
}
