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
class DeviceFingerprintRuleTest {

    @Mock
    private StringRedisTemplate redis;
    @Mock
    private SetOperations<String, String> setOps;

    private DeviceFingerprintRule rule;

    @BeforeEach
    void setUp() {
        lenient().when(redis.opsForSet()).thenReturn(setOps);
        rule = new DeviceFingerprintRule();
    }

    @Test
    void shouldReturnZeroWhenDeviceKnown() {
        when(setOps.isMember(anyString(), anyString())).thenReturn(true);
        var request = new FraudAnalysisRequest(
            "txn_001", UUID.randomUUID(), 100000L,
            "visa", "192.168.1.1", "device-hash-123", null, null
        );
        assertEquals(0, rule.evaluate(request, redis));
    }

    @Test
    void shouldReturnZeroWhenValueBelowThreshold() {
        when(setOps.isMember(anyString(), anyString())).thenReturn(false);
        var request = new FraudAnalysisRequest(
            "txn_001", UUID.randomUUID(), 30000L,
            "visa", "192.168.1.1", "device-hash-123", null, null
        );
        assertEquals(0, rule.evaluate(request, redis));
    }

    @Test
    void shouldReturnFifteenWhenNewDeviceAndHighValue() {
        when(setOps.isMember(anyString(), anyString())).thenReturn(false);
        var request = new FraudAnalysisRequest(
            "txn_001", UUID.randomUUID(), 100000L,
            "visa", "192.168.1.1", "device-hash-123", null, null
        );
        assertEquals(15, rule.evaluate(request, redis));
    }

    @Test
    void shouldReturnFifteenWhenNewDeviceAndExactlyThreshold() {
        when(setOps.isMember(anyString(), anyString())).thenReturn(false);
        var request = new FraudAnalysisRequest(
            "txn_001", UUID.randomUUID(), 50001L,
            "visa", "192.168.1.1", "new-device", null, null
        );
        assertEquals(15, rule.evaluate(request, redis));
    }

    @Test
    void shouldReturnZeroWhenNoDeviceFingerprint() {
        var request = new FraudAnalysisRequest(
            "txn_001", UUID.randomUUID(), 100000L,
            "visa", "192.168.1.1", null, null, null
        );
        assertEquals(0, rule.evaluate(request, redis));
    }

    @Test
    void shouldReturnCorrectReasonAndScore() {
        assertEquals("NEW_DEVICE_HIGH_VALUE", rule.getReason());
        assertEquals(15, rule.getScore());
    }
}
