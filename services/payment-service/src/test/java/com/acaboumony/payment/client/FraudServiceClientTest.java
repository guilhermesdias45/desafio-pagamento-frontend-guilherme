package com.acaboumony.payment.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FraudServiceClientTest {

    private FraudServiceClient client;

    @BeforeEach
    void setUp() {
        client = new FraudServiceClient("http://localhost:9999");
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
        assertTrue(result.reasons().contains("FALLBACK_TIMEOUT"));
    }
}
