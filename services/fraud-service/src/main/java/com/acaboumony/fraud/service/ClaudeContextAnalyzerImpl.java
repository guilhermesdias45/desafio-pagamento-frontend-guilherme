package com.acaboumony.fraud.service;

import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.Model;
import com.acaboumony.fraud.dto.request.FraudAnalysisRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

@Component
public class ClaudeContextAnalyzerImpl implements ClaudeContextAnalyzer {

    private static final Logger log = LoggerFactory.getLogger(ClaudeContextAnalyzerImpl.class);
    private static final int TIMEOUT_MS = 250;
    private static final String SYSTEM_PROMPT = """
        You are a fraud detection analyst. Analyze the risk of this transaction and respond with a JSON object only.
        {"adjustment": N, "reasoning": "..."}
        N must be an integer between -10 and +10 (negative = reduce risk score, positive = increase).
        """;

    private final com.anthropic.client.AnthropicClient client;
    private final ObjectMapper objectMapper;

    public ClaudeContextAnalyzerImpl(@Value("${anthropic.api-key:}") String apiKey) {
        this(apiKey != null && !apiKey.isBlank()
            ? AnthropicOkHttpClient.builder()
                .apiKey(apiKey)
                .timeout(Duration.ofMillis(TIMEOUT_MS))
                .build()
            : null);
    }

    ClaudeContextAnalyzerImpl(com.anthropic.client.AnthropicClient client) {
        this.client = client;
        this.objectMapper = new ObjectMapper();
        if (client == null) {
            log.warn("ANTHROPIC_API_KEY not configured; ClaudeContextAnalyzer will always return 0");
        }
    }

    @Override
    public int getContextualAdjustment(FraudAnalysisRequest request, int baseScore) {
        if (client == null) {
            return 0;
        }

        String userPrompt = buildUserPrompt(request, baseScore);
        MessageCreateParams params = MessageCreateParams.builder()
            .model(Model.CLAUDE_3_5_HAIKU_LATEST)
            .maxTokens(100)
            .system(SYSTEM_PROMPT)
            .addUserMessage(userPrompt)
            .build();

        try {
            Message response = client.messages().create(params);
            return parseAdjustment(response);
        } catch (Exception e) {
            log.warn("Claude API call failed for transaction {}: {}", request.transactionId(), e.getMessage());
            return 0;
        }
    }

    String buildUserPrompt(FraudAnalysisRequest request, int baseScore) {
        return String.format("""
            Transaction ID: %s
            Customer ID: %s
            Amount: %d cents
            Payment method: %s
            IP: %s
            Device: %s
            Score base: %d
            """,
            request.transactionId(),
            request.customerId(),
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
}
