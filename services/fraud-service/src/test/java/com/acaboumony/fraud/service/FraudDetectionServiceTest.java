package com.acaboumony.fraud.service;

import com.acaboumony.fraud.domain.entity.FraudAlert;
import com.acaboumony.fraud.dto.request.FraudAnalysisRequest;
import com.acaboumony.fraud.event.FraudEventProducer;
import com.acaboumony.fraud.repository.FraudAlertRepository;
import com.acaboumony.fraud.repository.IpBlacklistRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.quality.Strictness.LENIENT;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = LENIENT)
class FraudDetectionServiceTest {

    @Mock
    private StringRedisTemplate redis;
    @Mock
    private ZSetOperations<String, String> zSetOps;
    @Mock
    private ValueOperations<String, String> valueOps;
    @Mock
    private SetOperations<String, String> setOps;
    @Mock
    private ClaudeContextAnalyzer claudeAnalyzer;
    @Mock
    private FraudAlertRepository alertRepository;
    @Mock
    private FraudEventProducer eventProducer;
    @Mock
    private IpBlacklistRepository ipBlacklistRepository;

    @Captor
    private ArgumentCaptor<FraudAlert> alertCaptor;

    private FraudDetectionService fraudDetection;
    private FraudAnalysisRequest request;
    private RuleEngineService ruleEngine;

    @BeforeEach
    void setUp() {
        lenient().when(redis.opsForZSet()).thenReturn(zSetOps);
        lenient().when(redis.opsForValue()).thenReturn(valueOps);
        lenient().when(redis.opsForSet()).thenReturn(setOps);
        ruleEngine = new RuleEngineService(redis, ipBlacklistRepository);
        fraudDetection = new FraudDetectionService(ruleEngine, claudeAnalyzer, alertRepository, ipBlacklistRepository, redis, eventProducer, new SimpleMeterRegistry(),
            90, 70, 250L, 24L, 30, 5L);
        request = new FraudAnalysisRequest(
            "txn_001", UUID.randomUUID(), UUID.randomUUID(), 5000L,
            "visa", "192.168.1.1", null, null, null
        );
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

    @Test
    void ce001_firstCustomerWithMaxValue_shouldReturnScore20() {
        mockNoTrigger();
        when(valueOps.get(startsWith("fraud:avg:"))).thenReturn(null);
        when(valueOps.get(startsWith("fraud:purchase_count:"))).thenReturn(null);

        var firstPurchaseMax = new FraudAnalysisRequest(
            "txn_001", UUID.randomUUID(), UUID.randomUUID(), 99_999L,
            "visa", "192.168.1.1", null, null, null
        );

        var result = fraudDetection.score(firstPurchaseMax);
        assertEquals(20, result.score());
        assertEquals("APPROVE", result.decision());
        assertTrue(result.reasons().contains("FIRST_PURCHASE_MAX_VALUE"));
    }

    @Test
    void ce002_claudeUnavailable_shouldFallbackToBaseScore() {
        when(zSetOps.count(anyString(), anyDouble(), anyDouble())).thenReturn(3L);
        when(valueOps.get(anyString())).thenReturn(null);
        when(setOps.isMember(anyString(), anyString())).thenReturn(false);

        var borderlineRequest = new FraudAnalysisRequest(
            "txn_001", UUID.randomUUID(), UUID.randomUUID(), 5000L,
            "visa", "192.168.1.1", null, null, null
        );

        when(claudeAnalyzer.adjustWithReasoning(any(), anyInt())).thenReturn(new ClaudeContextAnalyzer.AdjustmentResult(0, null));

        var result = fraudDetection.score(borderlineRequest);
        assertNotNull(result);
    }

    @Test
    void ce003_highVelocityWithBlacklist_shouldBlockAndPersistAlert() {
        when(zSetOps.count(anyString(), anyDouble(), anyDouble())).thenReturn(5L);
        when(setOps.isMember(startsWith("fraud:ip_blacklist:"), anyString())).thenReturn(true);
        when(valueOps.get(anyString())).thenReturn("1");

        var attackRequest = new FraudAnalysisRequest(
            "txn_001", UUID.randomUUID(), UUID.randomUUID(), 5000L,
            "visa", "10.0.0.5", null, null, null
        );

        var result = fraudDetection.score(attackRequest);
        assertEquals("BLOCK", result.decision());
        assertTrue(result.score() >= 70);

        verify(alertRepository).save(alertCaptor.capture());
        FraudAlert saved = alertCaptor.getValue();
        assertEquals("txn_001", saved.getTransactionId());
        assertEquals("BLOCK", saved.getDecision().name());
        assertTrue(saved.getScore() >= 70);
        assertTrue(saved.getReasons().contains("VELOCITY_EXCEEDED"));
        assertTrue(saved.getReasons().contains("IP_BLACKLISTED"));

        verify(setOps).add(startsWith("fraud:ip_blacklist:"), eq("10.0.0.5"));
        verify(redis).expire(startsWith("fraud:ip_blacklist:"), eq(24L), eq(TimeUnit.HOURS));
    }

    @Test
    void lowRiskTransaction_shouldApproveWithoutSavingAlert() {
        mockNoTrigger();
        var result = fraudDetection.score(request);
        assertEquals("APPROVE", result.decision());
        assertEquals(0, result.score());
        verify(alertRepository, never()).save(any());
    }

    @Test
    void ce005_ipBlacklistedWithCleanHistory_shouldEnforceMinimum30() {
        when(zSetOps.count(anyString(), anyDouble(), anyDouble())).thenReturn(3L);
        when(setOps.isMember(startsWith("fraud:ip_blacklist:"), anyString())).thenReturn(true);
        when(valueOps.get(anyString())).thenReturn(null);
        when(claudeAnalyzer.adjustWithReasoning(any(), anyInt())).thenReturn(new ClaudeContextAnalyzer.AdjustmentResult(-10, null));

        var request = new FraudAnalysisRequest(
            "txn_001", UUID.randomUUID(), UUID.randomUUID(), 5000L,
            "visa", "10.0.0.5", null, null, null
        );

        var result = fraudDetection.score(request);
        assertTrue(result.score() >= 30);
        assertTrue(result.reasons().contains("IP_BLACKLISTED"));
    }

    @Test
    void globalTimeout_shouldReturnFallbackScore50() {
        mockNoTrigger();
        when(zSetOps.count(anyString(), anyDouble(), anyDouble())).thenReturn(3L);
        when(setOps.isMember(startsWith("fraud:ip_blacklist:"), anyString())).thenReturn(true);

        doAnswer(invocation -> {
            Thread.sleep(300);
            return new ClaudeContextAnalyzer.AdjustmentResult(-10, null);
        }).when(claudeAnalyzer).adjustWithReasoning(any(), anyInt());

        var result = fraudDetection.score(request);
        assertEquals(50, result.score());
        assertEquals("APPROVE", result.decision());
        assertTrue(result.reasons().contains("TIMEOUT_FALLBACK"));
    }

    @Test
    void ce004_merchantHighVelocity_shouldAddMerchantPatternScore() {
        UUID merchantId = UUID.randomUUID();
        FraudAnalysisRequest merchantRequest = new FraudAnalysisRequest(
            "txn_m1", UUID.randomUUID(), merchantId, 5000L,
            "visa", "192.168.1.1", null, null, null
        );

        when(zSetOps.count(startsWith("fraud:merchant_velocity:" + merchantId), anyDouble(), anyDouble())).thenReturn(5L);
        when(zSetOps.count(startsWith("fraud:velocity:"), anyDouble(), anyDouble())).thenReturn(0L);
        when(valueOps.get(anyString())).thenReturn(null);
        when(setOps.isMember(anyString(), anyString())).thenReturn(false);

        var result = fraudDetection.score(merchantRequest);

        assertTrue(result.score() >= 20, "Score should include MERCHANT_PATTERN (+20)");
        assertTrue(result.reasons().contains("MERCHANT_PATTERN"));
    }
}
