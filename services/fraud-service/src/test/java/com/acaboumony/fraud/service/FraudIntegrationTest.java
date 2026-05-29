package com.acaboumony.fraud.service;

import com.acaboumony.fraud.domain.entity.FraudAlert;
import com.acaboumony.fraud.dto.request.FraudAnalysisRequest;
import com.acaboumony.fraud.event.FraudEventProducer;
import com.acaboumony.fraud.repository.FraudAlertRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@SpringBootTest
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:fraud;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=update",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
    "spring.flyway.enabled=false",
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration"
})
class FraudIntegrationTest {

    @Autowired
    private FraudDetectionService fraudDetection;

    @Autowired
    private FraudAlertRepository alertRepository;

    @MockBean
    private StringRedisTemplate redis;

    @MockBean
    private ZSetOperations<String, String> zSetOps;

    @MockBean
    private ValueOperations<String, String> valueOps;

    @MockBean
    private SetOperations<String, String> setOps;

    @MockBean
    private ClaudeContextAnalyzer claudeAnalyzer;

    @MockBean
    private FraudEventProducer eventProducer;

    @MockBean
    private KafkaTemplate<String, Object> kafkaTemplate;

    @BeforeEach
    void setUp() {
        alertRepository.deleteAll();
        lenient().when(redis.opsForZSet()).thenReturn(zSetOps);
        lenient().when(redis.opsForValue()).thenReturn(valueOps);
        lenient().when(redis.opsForSet()).thenReturn(setOps);
        lenient().when(claudeAnalyzer.getContextualAdjustment(any(), anyInt())).thenReturn(0);
    }

    @Test
    void lowRiskTransaction_shouldApprove() {
        when(zSetOps.count(anyString(), anyDouble(), anyDouble())).thenReturn(0L);
        when(valueOps.get(anyString())).thenReturn("1");
        when(setOps.isMember(anyString(), anyString())).thenReturn(false);

        FraudAnalysisRequest request = new FraudAnalysisRequest(
            "txn_001", UUID.randomUUID(), UUID.randomUUID(), 5000L,
            "visa", "192.168.1.1", "device-123", null, null
        );

        var result = fraudDetection.score(request);

        assertEquals("APPROVE", result.decision());
        assertTrue(result.score() <= 69);
    }

    @Test
    void blockedTransaction_shouldPersistAlert() {
        UUID customerId = UUID.randomUUID();
        when(zSetOps.count(anyString(), anyDouble(), anyDouble())).thenReturn(5L);
        when(valueOps.get(anyString())).thenReturn("1");
        when(setOps.isMember(startsWith("fraud:ip_blacklist:"), anyString())).thenReturn(true);

        FraudAnalysisRequest request = new FraudAnalysisRequest(
            "txn_002", customerId, UUID.randomUUID(), 5000L,
            "visa", "10.0.0.5", null, null, null
        );

        var result = fraudDetection.score(request);

        assertEquals("BLOCK", result.decision());
        assertTrue(result.score() >= 70);

        List<FraudAlert> alerts = alertRepository.findAll();
        assertFalse(alerts.isEmpty());
        FraudAlert alert = alerts.getFirst();
        assertEquals("txn_002", alert.getTransactionId());
        assertEquals("BLOCK", alert.getDecision().name());
        assertTrue(alert.getReasons().contains("VELOCITY_EXCEEDED"));
    }
}
