package com.acaboumony.fraud.service;

import com.acaboumony.fraud.dto.request.FraudAnalysisRequest;
import com.acaboumony.fraud.repository.IpBlacklistRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.quality.Strictness.LENIENT;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = LENIENT)
class RuleEngineServiceTest {

    @Mock
    private StringRedisTemplate redis;
    @Mock
    private ZSetOperations<String, String> zSetOps;
    @Mock
    private ValueOperations<String, String> valueOps;
    @Mock
    private SetOperations<String, String> setOps;
    @Mock
    private IpBlacklistRepository ipBlacklistRepository;

    private RuleEngineService ruleEngine;
    private FraudAnalysisRequest request;

    @BeforeEach
    void setUp() {
        lenient().when(redis.opsForZSet()).thenReturn(zSetOps);
        lenient().when(redis.opsForValue()).thenReturn(valueOps);
        lenient().when(redis.opsForSet()).thenReturn(setOps);
        ruleEngine = new RuleEngineService(redis, ipBlacklistRepository);
        request = new FraudAnalysisRequest(
            "txn_001", UUID.randomUUID(), UUID.randomUUID(), 5000L,
            "visa", "192.168.1.1", null, null, null
        );
    }

    @Test
    void shouldReturnZeroWhenNoRulesTrigger() {
        mockNoTrigger();
        var result = ruleEngine.calculateBaseScore(request);
        assertEquals(0, result.score());
        assertTrue(result.reasons().isEmpty());
    }

    @Test
    void shouldAccumulateSingleRule() {
        mockNoTrigger();
        when(zSetOps.count(anyString(), anyDouble(), anyDouble())).thenReturn(3L);

        var result = ruleEngine.calculateBaseScore(request);
        assertEquals(40, result.score());
        assertEquals(2, result.reasons().size());
        assertTrue(result.reasons().contains("VELOCITY_EXCEEDED"));
        assertTrue(result.reasons().contains("MERCHANT_PATTERN"));
    }

    @Test
    void shouldDetectIpBlacklist() {
        mockNoTrigger();
        when(setOps.isMember(startsWith("fraud:ip_blacklist:"), anyString())).thenReturn(true);

        var result = ruleEngine.calculateBaseScore(request);
        assertEquals(40, result.score());
        assertTrue(result.reasons().contains("IP_BLACKLISTED"));
    }

    @Test
    void shouldAccumulateMultipleDifferentRules() {
        mockNoTrigger();
        when(zSetOps.count(anyString(), anyDouble(), anyDouble())).thenReturn(5L);
        when(setOps.isMember(startsWith("fraud:ip_blacklist:"), anyString())).thenReturn(true);

        var result = ruleEngine.calculateBaseScore(request);
        assertEquals(90, result.score());
        assertTrue(result.reasons().contains("VELOCITY_EXCEEDED"));
        assertTrue(result.reasons().contains("MERCHANT_PATTERN"));
        assertTrue(result.reasons().contains("IP_BLACKLISTED"));
        assertEquals(3, result.reasons().size());
    }

    @Test
    void shouldNotExceedOneHundred() {
        mockNoTrigger();
        when(zSetOps.count(anyString(), anyDouble(), anyDouble())).thenReturn(10L);
        when(setOps.isMember(startsWith("fraud:ip_blacklist:"), anyString())).thenReturn(true);
        when(valueOps.get(startsWith("fraud:avg:"))).thenReturn("100");
        when(setOps.isMember(startsWith("fraud:devices:"), anyString())).thenReturn(false);

        var highAmountRequest = new FraudAnalysisRequest(
            "txn_001", UUID.randomUUID(), UUID.randomUUID(), 200_000L,
            "visa", "10.0.0.5", "new-device", null, null
        );

        var result = ruleEngine.calculateBaseScore(highAmountRequest);
        assertTrue(result.score() <= 100);
    }

    private void mockNoTrigger() {
        when(zSetOps.count(anyString(), anyDouble(), anyDouble())).thenReturn(0L);

        when(valueOps.get(startsWith("fraud:avg:"))).thenReturn("10000");
        when(valueOps.get(startsWith("fraud:card:"))).thenReturn("1");
        when(valueOps.get(startsWith("fraud:purchase_count:"))).thenReturn("1");
        when(valueOps.get(startsWith("fraud:country:ip:"))).thenReturn("BR");
        when(valueOps.get(startsWith("fraud:country:customer:"))).thenReturn("BR");
        when(valueOps.get(startsWith("fraud:ip_last:customer:"))).thenReturn("192.168.1.1");
        when(valueOps.get(startsWith("fraud:ip_time:customer:"))).thenReturn("0");

        when(setOps.isMember(startsWith("fraud:ip_blacklist:"), anyString())).thenReturn(false);
        when(setOps.isMember(startsWith("fraud:devices:"), anyString())).thenReturn(true);
    }
}
