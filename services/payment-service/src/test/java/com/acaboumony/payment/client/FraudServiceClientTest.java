package com.acaboumony.payment.client;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FraudServiceClientTest {

    private FraudServiceClient client;

    @BeforeEach
    void setUp() {
        client = new FraudServiceClient("http://localhost:9999",
            CircuitBreakerRegistry.ofDefaults());
    }

    @Test
    void score_whenServiceUnavailable_returnsFallback() {
        var request = new FraudServiceClient.FraudAnalysisRequest(
            "txn_test", UUID.randomUUID(), UUID.randomUUID(), 5000L,
            "visa", "127.0.0.1", null, null, null
        );

        var result = client.score(request);

        assertNotNull(result);
        assertEquals(50, result.score());
        assertEquals("APPROVE", result.decision());
        assertTrue(result.reasons().contains("FALLBACK_CIRCUIT_BREAKER"));
    }

    @Test
    void fraudAnalysisRequest_recordConstructor_works() {
        var request = new FraudServiceClient.FraudAnalysisRequest(
            "txn_001", UUID.randomUUID(), UUID.randomUUID(), 10000L,
            "visa", "192.168.1.1", "fp_abc", 1.23, 4.56
        );

        assertEquals("txn_001", request.transactionId());
        assertNotNull(request.customerId());
        assertNotNull(request.merchantId());
        assertEquals(10000L, request.amountInCents());
        assertEquals("visa", request.paymentMethodId());
        assertEquals("192.168.1.1", request.ipAddress());
        assertEquals("fp_abc", request.deviceFingerprint());
        assertEquals(1.23, request.latitude());
        assertEquals(4.56, request.longitude());
    }

    @Test
    void fraudAnalysisRequest_withNullOptionalFields() {
        var request = new FraudServiceClient.FraudAnalysisRequest(
            "txn_002", UUID.randomUUID(), UUID.randomUUID(), 5000L,
            "master", "10.0.0.1", null, null, null
        );

        assertNull(request.deviceFingerprint());
        assertNull(request.latitude());
        assertNull(request.longitude());
    }

    @Test
    void fraudScoreResult_recordConstructor_works() {
        var result = new FraudServiceClient.FraudScoreResult(
            75, "BLOCK", List.of("HIGH_AMOUNT", "NEW_DEVICE"), 120L
        );

        assertEquals(75, result.score());
        assertEquals("BLOCK", result.decision());
        assertEquals(2, result.reasons().size());
        assertTrue(result.reasons().contains("HIGH_AMOUNT"));
        assertTrue(result.reasons().contains("NEW_DEVICE"));
        assertEquals(120L, result.analysisTimeMs());
    }

    @Test
    void score_fallbackResultHasExpectedReasonsList() {
        var result = client.score(new FraudServiceClient.FraudAnalysisRequest(
            "txn_003", UUID.randomUUID(), UUID.randomUUID(), 2000L,
            "elo", "10.0.0.2", "fp_xyz", null, null
        ));

        assertNotNull(result.reasons());
        assertFalse(result.reasons().isEmpty());
        assertEquals(1, result.reasons().size());
    }
}
