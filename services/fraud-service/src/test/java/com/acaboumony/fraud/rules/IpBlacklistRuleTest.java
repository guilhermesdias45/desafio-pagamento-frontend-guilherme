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
import org.springframework.data.redis.core.SetOperations;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = LENIENT)
class IpBlacklistRuleTest {

    @Mock
    private StringRedisTemplate redis;
    @Mock
    private SetOperations<String, String> setOps;

    private IpBlacklistRule rule;

    @BeforeEach
    void setUp() {
        lenient().when(redis.opsForSet()).thenReturn(setOps);
        rule = new IpBlacklistRule();
    }

    @Test
    void shouldReturnZeroWhenIpNotBlacklisted() {
        when(setOps.isMember(anyString(), anyString())).thenReturn(false);
        var request = new FraudAnalysisRequest(
            "txn_001", UUID.randomUUID(), 5000L,
            "visa", "192.168.1.1", null, null, null
        );
        assertEquals(0, rule.evaluate(request, redis));
    }

    @Test
    void shouldReturnFortyWhenIpIsBlacklisted() {
        when(setOps.isMember(anyString(), anyString())).thenReturn(true);
        var request = new FraudAnalysisRequest(
            "txn_001", UUID.randomUUID(), 5000L,
            "visa", "192.168.1.1", null, null, null
        );
        assertEquals(40, rule.evaluate(request, redis));
    }

    @Test
    void shouldUseIpSpecificRedisKey() {
        when(setOps.isMember(anyString(), anyString())).thenReturn(true);
        var request = new FraudAnalysisRequest(
            "txn_001", UUID.randomUUID(), 5000L,
            "visa", "10.0.0.5", null, null, null
        );
        rule.evaluate(request, redis);
        verify(setOps).isMember(eq("fraud:ip_blacklist:10.0.0.5"), eq("10.0.0.5"));
    }

    @Test
    void shouldReturnCorrectReasonAndScore() {
        assertEquals("IP_BLACKLISTED", rule.getReason());
        assertEquals(40, rule.getScore());
    }
}
