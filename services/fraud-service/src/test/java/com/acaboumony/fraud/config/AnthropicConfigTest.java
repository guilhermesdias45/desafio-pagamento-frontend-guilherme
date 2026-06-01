package com.acaboumony.fraud.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AnthropicConfigTest {

    @Test
    void shouldReturnNullWhenApiKeyIsBlank() {
        AnthropicConfig config = new AnthropicConfig();
        assertNull(config.anthropicClient(""));
        assertNull(config.anthropicClient("  "));
        assertNull(config.anthropicClient(null));
    }

    @Test
    void shouldReturnClientWhenApiKeyIsValid() {
        AnthropicConfig config = new AnthropicConfig();
        assertNotNull(config.anthropicClient("sk-test-key-12345"));
    }
}
