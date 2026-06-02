package com.acaboumony.fraud.service;

import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.Model;
import com.acaboumony.fraud.dto.request.FraudAnalysisRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class ClaudeContextAnalyzerImpl implements ClaudeContextAnalyzer {

    private static final Logger log = LoggerFactory.getLogger(ClaudeContextAnalyzerImpl.class);
    private static final String SYSTEM_PROMPT = """
        You are a fraud detection analyst. Analyze the risk of this transaction and respond with a JSON object only.
        {"adjustment": N, "reasoning": "..."}
        N must be an integer between -10 and +10 (negative = reduce risk score, positive = increase).
        Consider both customer behavior and merchant context when assessing risk.
        """;

    private final com.anthropic.client.AnthropicClient client;
    private final ObjectMapper objectMapper;
    private final CircuitBreaker circuitBreaker;

    public ClaudeContextAnalyzerImpl(
            com.anthropic.client.AnthropicClient client,
            CircuitBreakerRegistry circuitBreakerRegistry) {
        this.client = client;
        this.objectMapper = new ObjectMapper();
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker("claudeApi");
        if (client == null) {
            log.warn("ANTHROPIC_API_KEY not configured; ClaudeContextAnalyzer will always return 0");
        }
    }

    @Override
    public int getContextualAdjustment(FraudAnalysisRequest request, int baseScore) {
        if (client == null) {
            return 0;
        }

        try {
            return circuitBreaker.executeSupplier(() -> {
                String userPrompt = buildUserPrompt(request, baseScore);
                MessageCreateParams params = MessageCreateParams.builder()
                    .model(Model.CLAUDE_3_5_HAIKU_LATEST)
                    .maxTokens(100)
                    .system(SYSTEM_PROMPT)
                    .addUserMessage(userPrompt)
                    .build();
                Message response = client.messages().create(params);
                return parseAdjustment(response);
            });
        } catch (Exception e) {
            log.warn("Claude API call failed or circuit open for transaction {}: {}", request.transactionId(), e.getMessage());
            return 0;
        }
    }

    @Override
    public AdjustmentResult adjustWithReasoning(FraudAnalysisRequest request, int baseScore) {
        if (client == null) {
            return new AdjustmentResult(0, null);
        }

        try {
            return circuitBreaker.executeSupplier(() -> {
                String userPrompt = buildUserPrompt(request, baseScore);
                MessageCreateParams params = MessageCreateParams.builder()
                    .model(Model.CLAUDE_3_5_HAIKU_LATEST)
                    .maxTokens(100)
                    .system(SYSTEM_PROMPT)
                    .addUserMessage(userPrompt)
                    .build();
                Message response = client.messages().create(params);
                return parseAdjustmentWithReasoning(response);
            });
        } catch (Exception e) {
            log.warn("Claude API call failed or circuit open for transaction {}: {}", request.transactionId(), e.getMessage());
            return new AdjustmentResult(0, null);
        }
    }

    String buildUserPrompt(FraudAnalysisRequest request, int baseScore) {
        return String.format("""
            Transaction ID: %s
            Customer ID: %s
            Merchant ID: %s
            Amount: %d cents
            Payment method: %s
            IP: %s
            Device: %s
            Score base: %d
            Merchant context: this merchant's transaction velocity in the last 5 minutes is tracked separately.
            Consider merchant risk patterns: high-velocity merchants or merchants with repeated fraud alerts
            may indicate compromised payment infrastructure rather than individual customer fraud.
            If the merchant has unusual transaction patterns, the contextual adjustment should factor this in.
            """,
            request.transactionId(),
            request.customerId(),
            request.merchantId(),
            request.amountInCents(),
            request.paymentMethodId(),
            request.ipAddress(),
            Optional.ofNullable(request.deviceFingerprint()).orElse("unknown"),
            baseScore);
    }

    int parseAdjustment(Message response) {
        StringBuilder sb = new StringBuilder();
        for (var block : response.content()) {
            block.text().ifPresent(t -> sb.append(t.text()));
        }
        return parseAdjustmentText(sb.toString());
    }

    AdjustmentResult parseAdjustmentWithReasoning(Message response) {
        StringBuilder sb = new StringBuilder();
        for (var block : response.content()) {
            block.text().ifPresent(t -> sb.append(t.text()));
        }
        return parseAdjustmentWithReasoningText(sb.toString());
    }

    int parseAdjustmentText(String text) {
        if (text.isBlank()) {
            log.warn("Claude returned empty response");
            return 0;
        }

        try {
            JsonNode root = objectMapper.readTree(text);
            int adjustment = root.path("adjustment").asInt(0);
            return Math.clamp(adjustment, -10, 10);
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse Claude response: {}", text);
            return 0;
        }
    }

    AdjustmentResult parseAdjustmentWithReasoningText(String text) {
        if (text.isBlank()) {
            log.warn("Claude returned empty response");
            return new AdjustmentResult(0, null);
        }

        try {
            JsonNode root = objectMapper.readTree(text);
            int adjustment = Math.clamp(root.path("adjustment").asInt(0), -10, 10);
            String reasoning = root.path("reasoning").asText(null);
            return new AdjustmentResult(adjustment, reasoning);
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse Claude response: {}", text);
            return new AdjustmentResult(0, null);
        }
    }
}
