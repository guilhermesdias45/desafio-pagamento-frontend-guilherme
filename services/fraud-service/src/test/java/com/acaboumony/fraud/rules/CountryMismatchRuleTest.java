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
class CountryMismatchRuleTest {

    @Mock
    private StringRedisTemplate redis;
    @Mock
    private ValueOperations<String, String> valueOps;

    private CountryMismatchRule rule;

    @BeforeEach
    void setUp() {
        lenient().when(redis.opsForValue()).thenReturn(valueOps);
        rule = new CountryMismatchRule();
    }

    @Test
    void shouldReturnZeroWhenIpAndCustomerCountryMatch() {
        when(valueOps.get(anyString())).thenReturn("BR");
        var request = new FraudAnalysisRequest(
            "txn_001", UUID.randomUUID(), 5000L,
            "visa", "192.168.1.1", null, null, null
        );
        assertEquals(0, rule.evaluate(request, redis));
    }

    @Test
    void shouldReturnTwentyWhenCountriesDiffer() {
        when(valueOps.get(startsWith("fraud:country:ip:"))).thenReturn("US");
        when(valueOps.get(startsWith("fraud:country:customer:"))).thenReturn("BR");
        var request = new FraudAnalysisRequest(
            "txn_001", UUID.randomUUID(), 5000L,
            "visa", "192.168.1.1", null, null, null
        );
        assertEquals(20, rule.evaluate(request, redis));
    }

    @Test
    void shouldReturnZeroWhenIpCountryUnknown() {
        when(valueOps.get(anyString())).thenReturn(null);
        var request = new FraudAnalysisRequest(
            "txn_001", UUID.randomUUID(), 5000L,
            "visa", "192.168.1.1", null, null, null
        );
        assertEquals(0, rule.evaluate(request, redis));
    }

    @Test
    void shouldReturnCorrectReasonAndScore() {
        assertEquals("COUNTRY_MISMATCH", rule.getReason());
        assertEquals(20, rule.getScore());
    }
}
