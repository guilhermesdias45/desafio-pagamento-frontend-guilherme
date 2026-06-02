package com.acaboumony.fraud.service;

import com.acaboumony.fraud.dto.request.FraudAnalysisRequest;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ClaudeContextAnalyzerImplTest {

    private final FraudAnalysisRequest request = new FraudAnalysisRequest(
        "txn_001", UUID.randomUUID(), UUID.randomUUID(), 5000L,
        "visa", "192.168.1.1", null, null, null
    );

    private ClaudeContextAnalyzerImpl createAnalyzer() {
        return new ClaudeContextAnalyzerImpl(null, CircuitBreakerRegistry.ofDefaults());
    }

    @Test
    void nullClient_shouldReturnZero() {
        var analyzer = createAnalyzer();
        assertEquals(0, analyzer.getContextualAdjustment(request, 50));
    }

    @Test
    void buildUserPrompt_containsTransactionInfo() {
        var analyzer = createAnalyzer();
        String prompt = analyzer.buildUserPrompt(request, 50);
        assertAll(
            () -> assertTrue(prompt.contains("txn_001")),
            () -> assertTrue(prompt.contains("5000")),
            () -> assertTrue(prompt.contains("visa")),
            () -> assertTrue(prompt.contains("192.168.1.1")),
            () -> assertTrue(prompt.contains("unknown")),
            () -> assertTrue(prompt.contains("50"))
        );
    }

    @Test
    void parseAdjustmentText_validJson_returnsAdjustment() {
        var analyzer = createAnalyzer();
        assertEquals(5, analyzer.parseAdjustmentText("{\"adjustment\": 5, \"reasoning\": \"test\"}"));
    }

    @Test
    void parseAdjustmentText_negativeAdjustment_returnsNegative() {
        var analyzer = createAnalyzer();
        assertEquals(-3, analyzer.parseAdjustmentText("{\"adjustment\": -3}"));
    }

    @Test
    void parseAdjustmentText_aboveMax_clampsTo10() {
        var analyzer = createAnalyzer();
        assertEquals(10, analyzer.parseAdjustmentText("{\"adjustment\": 15}"));
    }

    @Test
    void parseAdjustmentText_belowMin_clampsToMinus10() {
        var analyzer = createAnalyzer();
        assertEquals(-10, analyzer.parseAdjustmentText("{\"adjustment\": -20}"));
    }

    @Test
    void parseAdjustmentText_blank_returnsZero() {
        var analyzer = createAnalyzer();
        assertEquals(0, analyzer.parseAdjustmentText("   "));
    }

    @Test
    void parseAdjustmentText_missingField_returnsZero() {
        var analyzer = createAnalyzer();
        assertEquals(0, analyzer.parseAdjustmentText("{\"reasoning\": \"ok\"}"));
    }

    @Test
    void parseAdjustmentText_invalidJson_returnsZero() {
        var analyzer = createAnalyzer();
        assertEquals(0, analyzer.parseAdjustmentText("not json"));
    }

    @Test
    void adjustWithReasoning_nullClient_returnsZeroAdjustmentAndNullReasoning() {
        var analyzer = createAnalyzer();
        var result = analyzer.adjustWithReasoning(request, 50);
        assertEquals(0, result.adjustment());
        assertNull(result.reasoning());
    }

    @Test
    void parseAdjustmentWithReasoningText_validJson_returnsBothFields() {
        var analyzer = createAnalyzer();
        var result = analyzer.parseAdjustmentWithReasoningText("{\"adjustment\": -5, \"reasoning\": \"low risk\"}");
        assertEquals(-5, result.adjustment());
        assertEquals("low risk", result.reasoning());
    }

    @Test
    void parseAdjustmentWithReasoningText_blank_returnsZeroAndNull() {
        var analyzer = createAnalyzer();
        var result = analyzer.parseAdjustmentWithReasoningText("   ");
        assertEquals(0, result.adjustment());
        assertNull(result.reasoning());
    }

    @Test
    void parseAdjustmentWithReasoningText_invalidJson_returnsZeroAndNull() {
        var analyzer = createAnalyzer();
        var result = analyzer.parseAdjustmentWithReasoningText("not json");
        assertEquals(0, result.adjustment());
        assertNull(result.reasoning());
    }

    @Test
    void parseAdjustmentWithReasoningText_clampsValues() {
        var analyzer = createAnalyzer();
        var result = analyzer.parseAdjustmentWithReasoningText("{\"adjustment\": 15}");
        assertEquals(10, result.adjustment());
        assertNull(result.reasoning());
    }

    @Test
    void buildUserPrompt_containsMerchantId() {
        var analyzer = createAnalyzer();
        String prompt = analyzer.buildUserPrompt(request, 50);
        assertTrue(prompt.contains("Merchant ID:"));
    }
}
