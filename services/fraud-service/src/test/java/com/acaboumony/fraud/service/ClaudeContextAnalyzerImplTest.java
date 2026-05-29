package com.acaboumony.fraud.service;

import com.acaboumony.fraud.dto.request.FraudAnalysisRequest;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ClaudeContextAnalyzerImplTest {

    private final FraudAnalysisRequest request = new FraudAnalysisRequest(
        "txn_001", UUID.randomUUID(), 5000L,
        "visa", "192.168.1.1", null, null, null
    );

    @Test
    void noApiKey_shouldReturnZero() {
        var analyzer = new ClaudeContextAnalyzerImpl("");
        assertEquals(0, analyzer.getContextualAdjustment(request, 50));
    }

    @Test
    void blankApiKey_shouldReturnZero() {
        var analyzer = new ClaudeContextAnalyzerImpl("   ");
        assertEquals(0, analyzer.getContextualAdjustment(request, 50));
    }

    @Test
    void nullClient_shouldReturnZero() {
        var analyzer = new ClaudeContextAnalyzerImpl((com.anthropic.client.AnthropicClient) null);
        assertEquals(0, analyzer.getContextualAdjustment(request, 50));
    }

    @Test
    void buildUserPrompt_containsTransactionInfo() {
        var analyzer = new ClaudeContextAnalyzerImpl((com.anthropic.client.AnthropicClient) null);
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
        var analyzer = new ClaudeContextAnalyzerImpl((com.anthropic.client.AnthropicClient) null);
        assertEquals(5, analyzer.parseAdjustmentText("{\"adjustment\": 5, \"reasoning\": \"test\"}"));
    }

    @Test
    void parseAdjustmentText_negativeAdjustment_returnsNegative() {
        var analyzer = new ClaudeContextAnalyzerImpl((com.anthropic.client.AnthropicClient) null);
        assertEquals(-3, analyzer.parseAdjustmentText("{\"adjustment\": -3}"));
    }

    @Test
    void parseAdjustmentText_aboveMax_clampsTo10() {
        var analyzer = new ClaudeContextAnalyzerImpl((com.anthropic.client.AnthropicClient) null);
        assertEquals(10, analyzer.parseAdjustmentText("{\"adjustment\": 15}"));
    }

    @Test
    void parseAdjustmentText_belowMin_clampsToMinus10() {
        var analyzer = new ClaudeContextAnalyzerImpl((com.anthropic.client.AnthropicClient) null);
        assertEquals(-10, analyzer.parseAdjustmentText("{\"adjustment\": -20}"));
    }

    @Test
    void parseAdjustmentText_blank_returnsZero() {
        var analyzer = new ClaudeContextAnalyzerImpl((com.anthropic.client.AnthropicClient) null);
        assertEquals(0, analyzer.parseAdjustmentText("   "));
    }

    @Test
    void parseAdjustmentText_missingField_returnsZero() {
        var analyzer = new ClaudeContextAnalyzerImpl((com.anthropic.client.AnthropicClient) null);
        assertEquals(0, analyzer.parseAdjustmentText("{\"reasoning\": \"ok\"}"));
    }

    @Test
    void parseAdjustmentText_invalidJson_returnsZero() {
        var analyzer = new ClaudeContextAnalyzerImpl((com.anthropic.client.AnthropicClient) null);
        assertEquals(0, analyzer.parseAdjustmentText("not json"));
    }
}
