package com.acaboumony.fraud.config;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class AnthropicConfig {

    private static final int TIMEOUT_MS = 250;

    @Bean
    public AnthropicClient anthropicClient(@Value("${anthropic.api-key:}") String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return null;
        }
        return AnthropicOkHttpClient.builder()
            .apiKey(apiKey)
            .timeout(Duration.ofMillis(TIMEOUT_MS))
            .build();
    }
}
