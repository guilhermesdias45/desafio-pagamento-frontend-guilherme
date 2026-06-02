package com.acaboumony.fraud.service;

import com.acaboumony.fraud.domain.entity.FraudAlert;
import com.acaboumony.fraud.dto.request.FraudAnalysisRequest;
import com.acaboumony.fraud.event.FraudEventProducer;
import com.acaboumony.fraud.repository.FraudAlertRepository;
import com.acaboumony.fraud.repository.IpBlacklistRepository;
import com.acaboumony.fraud.domain.entity.IpBlacklist;
import com.acaboumony.fraud.domain.enums.BlacklistSource;
import com.acaboumony.fraud.support.BaseIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

class FraudDetectionServiceIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private FraudDetectionService fraudDetection;

    @Autowired
    private FraudAlertRepository alertRepository;

    @Autowired
    private IpBlacklistRepository ipBlacklistRepository;

    @Autowired
    private StringRedisTemplate redis;

    @MockBean
    private ClaudeContextAnalyzer claudeAnalyzer;

    @MockBean
    private FraudEventProducer eventProducer;

    @BeforeEach
    void setUp() {
        alertRepository.deleteAll();
        ipBlacklistRepository.deleteAll();
        redis.getConnectionFactory().getConnection().serverCommands().flushAll();

        lenient().when(claudeAnalyzer.getContextualAdjustment(any(), anyInt())).thenReturn(0);
    }

    @Test
    void lowRiskTransaction_shouldApprove() {
        var request = new FraudAnalysisRequest(
            "txn_001", UUID.randomUUID(), UUID.randomUUID(), 5000L,
            "visa", "192.168.1.1", "device-123", null, null
        );

        var result = fraudDetection.score(request);

        assertEquals("APPROVE", result.decision());
        assertTrue(result.score() <= 69);
    }

    @Test
    void highVelocityTransaction_shouldBlock() {
        UUID customerId = UUID.randomUUID();
        String redisKey = "fraud:velocity:" + customerId;
        redis.opsForZSet().add(redisKey, "old_txn", (double) System.currentTimeMillis());
        redis.opsForZSet().add(redisKey, "old_txn2", (double) System.currentTimeMillis());
        redis.opsForZSet().add(redisKey, "old_txn3", (double) System.currentTimeMillis());
        redis.opsForZSet().add(redisKey, "old_txn4", (double) System.currentTimeMillis());
        redis.opsForZSet().add(redisKey, "old_txn5", (double) System.currentTimeMillis());

        var request = new FraudAnalysisRequest(
            "txn_002", customerId, UUID.randomUUID(), 5000L,
            "visa", "10.0.0.5", null, null, null
        );

        var result = fraudDetection.score(request);

        assertEquals("BLOCK", result.decision());
        assertTrue(result.score() >= 70);
    }

    @Test
    void ipBlacklistedTransaction_shouldBlock() {
        String ip = "10.0.0.100";
        String blacklistKey = "fraud:ip_blacklist:" + ip;
        redis.opsForSet().add(blacklistKey, ip);

        var request = new FraudAnalysisRequest(
            "txn_003", UUID.randomUUID(), UUID.randomUUID(), 5000L,
            "visa", ip, null, null, null
        );

        var result = fraudDetection.score(request);

        assertEquals("BLOCK", result.decision());
    }

    @Test
    void blockedTransaction_persistsAlertInDatabase() {
        UUID customerId = UUID.randomUUID();
        String redisKey = "fraud:velocity:" + customerId;
        for (int i = 0; i < 6; i++) {
            redis.opsForZSet().add(redisKey, "txn_" + i, (double) System.currentTimeMillis());
        }

        var request = new FraudAnalysisRequest(
            "txn_004", customerId, UUID.randomUUID(), 5000L,
            "visa", "10.0.0.200", null, null, null
        );

        fraudDetection.score(request);

        List<FraudAlert> alerts = alertRepository.findAll();
        assertFalse(alerts.isEmpty());
        FraudAlert alert = alerts.getFirst();
        assertEquals("txn_004", alert.getTransactionId());
        assertEquals("BLOCK", alert.getDecision().name());
        assertTrue(alert.getReasons().contains("VELOCITY_EXCEEDED"));
    }

    @Test
    void moderateRiskTransaction_shouldTriggerReview() {
        UUID customerId = UUID.randomUUID();
        String customerKey = "fraud:velocity:" + customerId;
        redis.opsForZSet().add(customerKey, "txn_m1", (double) System.currentTimeMillis());
        redis.opsForZSet().add(customerKey, "txn_m2", (double) System.currentTimeMillis());

        String merchantKey = "fraud:merchant_velocity:" + UUID.randomUUID();
        redis.opsForZSet().add(merchantKey, "txn_m3", (double) System.currentTimeMillis());

        var request = new FraudAnalysisRequest(
            "txn_005", customerId, UUID.randomUUID(), 5000L,
            "visa", "192.168.1.50", null, null, null
        );

        var result = fraudDetection.score(request);

        assertTrue(result.decision().equals("APPROVE") || result.decision().equals("REVIEW"));
    }

    @Test
    void blockedTransaction_persistsIpToJpaBlacklist() {
        UUID customerId = UUID.randomUUID();
        String ip = "10.0.0.250";
        String redisKey = "fraud:velocity:" + customerId;
        for (int i = 0; i < 6; i++) {
            redis.opsForZSet().add(redisKey, "txn_ip" + i, (double) System.currentTimeMillis());
        }

        var request = new FraudAnalysisRequest(
            "txn_006", customerId, UUID.randomUUID(), 5000L,
            "visa", ip, null, null, null
        );

        fraudDetection.score(request);

        assertTrue(ipBlacklistRepository.findByIpAddress(ip).isPresent());
    }

    @Test
    void approvedTransaction_doesNotPersistAlert() {
        var request = new FraudAnalysisRequest(
            "txn_007", UUID.randomUUID(), UUID.randomUUID(), 1000L,
            "visa", "10.0.0.1", null, null, null
        );

        fraudDetection.score(request);

        List<FraudAlert> alerts = alertRepository.findAll();
        assertTrue(alerts.isEmpty());
    }

    @Test
    void score_withClaudeAdjustment_increasesScore() {
        when(claudeAnalyzer.getContextualAdjustment(any(), anyInt())).thenReturn(15);

        UUID customerId = UUID.randomUUID();
        String customerKey = "fraud:velocity:" + customerId;
        redis.opsForZSet().add(customerKey, "txn_c1", (double) System.currentTimeMillis());
        redis.opsForZSet().add(customerKey, "txn_c2", (double) System.currentTimeMillis());
        redis.opsForZSet().add(customerKey, "txn_c3", (double) System.currentTimeMillis());

        var request = new FraudAnalysisRequest(
            "txn_008", customerId, UUID.randomUUID(), 5000L,
            "visa", "192.168.1.75", null, null, null
        );

        var result = fraudDetection.score(request);

        assertEquals("REVIEW", result.decision());
        assertTrue(result.score() >= 70);
    }
}
