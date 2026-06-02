package com.acaboumony.fraud.rules;

import com.acaboumony.fraud.dto.request.FraudAnalysisRequest;
import com.acaboumony.fraud.repository.IpBlacklistRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import static org.mockito.quality.Strictness.LENIENT;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.SetOperations;

import java.util.Optional;
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
    @Mock
    private IpBlacklistRepository ipBlacklistRepository;

    private IpBlacklistRule rule;

    @BeforeEach
    void setUp() {
        lenient().when(redis.opsForSet()).thenReturn(setOps);
        rule = new IpBlacklistRule(ipBlacklistRepository);
    }

    @Test
    void shouldReturnZeroWhenIpNotBlacklisted() {
        when(setOps.isMember(anyString(), anyString())).thenReturn(false);
        var request = new FraudAnalysisRequest(
            "txn_001", UUID.randomUUID(), UUID.randomUUID(), 5000L,
            "visa", "192.168.1.1", null, null, null
        );
        assertEquals(0, rule.evaluate(request, redis));
    }

    @Test
    void shouldReturnFortyWhenIpIsBlacklisted() {
        when(setOps.isMember(anyString(), anyString())).thenReturn(true);
        var request = new FraudAnalysisRequest(
            "txn_001", UUID.randomUUID(), UUID.randomUUID(), 5000L,
            "visa", "192.168.1.1", null, null, null
        );
        assertEquals(40, rule.evaluate(request, redis));
    }

    @Test
    void shouldUseIpSpecificRedisKey() {
        when(setOps.isMember(anyString(), anyString())).thenReturn(true);
        var request = new FraudAnalysisRequest(
            "txn_001", UUID.randomUUID(), UUID.randomUUID(), 5000L,
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

    @Test
    void shouldFallbackToDatabaseWhenRedisMiss() {
        when(setOps.isMember(anyString(), anyString())).thenReturn(false);
        when(ipBlacklistRepository.findByIpAddress("10.0.0.5")).thenReturn(
            Optional.of(com.acaboumony.fraud.domain.entity.IpBlacklist.builder()
                .ipAddress("10.0.0.5")
                .expiresAt(java.time.OffsetDateTime.now().plusHours(1))
                .build())
        );
        when(setOps.add(anyString(), anyString())).thenReturn(1L);

        var request = new FraudAnalysisRequest(
            "txn_001", UUID.randomUUID(), UUID.randomUUID(), 5000L,
            "visa", "10.0.0.5", null, null, null
        );
        assertEquals(40, rule.evaluate(request, redis));
        verify(setOps).add(eq("fraud:ip_blacklist:10.0.0.5"), eq("10.0.0.5"));
    }

    @Test
    void shouldReturnZeroWhenDatabaseAlsoMisses() {
        when(setOps.isMember(anyString(), anyString())).thenReturn(false);
        when(ipBlacklistRepository.findByIpAddress("10.0.0.5")).thenReturn(Optional.empty());

        var request = new FraudAnalysisRequest(
            "txn_001", UUID.randomUUID(), UUID.randomUUID(), 5000L,
            "visa", "10.0.0.5", null, null, null
        );
        assertEquals(0, rule.evaluate(request, redis));
    }

    @Test
    void shouldReturnZeroWhenDatabaseEntryExpired() {
        when(setOps.isMember(anyString(), anyString())).thenReturn(false);
        when(ipBlacklistRepository.findByIpAddress("10.0.0.5")).thenReturn(
            Optional.of(com.acaboumony.fraud.domain.entity.IpBlacklist.builder()
                .ipAddress("10.0.0.5")
                .expiresAt(java.time.OffsetDateTime.now().minusHours(1))
                .build())
        );

        var request = new FraudAnalysisRequest(
            "txn_001", UUID.randomUUID(), UUID.randomUUID(), 5000L,
            "visa", "10.0.0.5", null, null, null
        );
        assertEquals(0, rule.evaluate(request, redis));
    }

    @Test
    void shouldReturnZeroWhenDatabaseThrowsException() {
        when(setOps.isMember(anyString(), anyString())).thenReturn(false);
        when(ipBlacklistRepository.findByIpAddress("10.0.0.5")).thenThrow(new RuntimeException("DB down"));

        var request = new FraudAnalysisRequest(
            "txn_001", UUID.randomUUID(), UUID.randomUUID(), 5000L,
            "visa", "10.0.0.5", null, null, null
        );
        assertEquals(0, rule.evaluate(request, redis));
    }
}
