package com.acaboumony.payment.service;

import com.acaboumony.payment.dto.response.TransactionResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IdempotencyServiceTest {

    @Mock StringRedisTemplate redisTemplate;
    @Mock ValueOperations<String, String> valueOps;

    IdempotencyService idempotencyService;

    private static final UUID IDEMPOTENCY_KEY = UUID.randomUUID();
    private static final String KEY = "pay:idem:" + IDEMPOTENCY_KEY;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        idempotencyService = new IdempotencyService(redisTemplate);
    }

    @Test
    void isDuplicate_returns_true_when_key_exists() {
        when(redisTemplate.hasKey(KEY)).thenReturn(true);
        assertThat(idempotencyService.isDuplicate(IDEMPOTENCY_KEY)).isTrue();
    }

    @Test
    void isDuplicate_returns_false_when_key_not_exists() {
        when(redisTemplate.hasKey(KEY)).thenReturn(false);
        assertThat(idempotencyService.isDuplicate(IDEMPOTENCY_KEY)).isFalse();
    }

    @Test
    void store_serializes_and_stores_in_redis() {
        UUID orderId = UUID.randomUUID();
        TransactionResponse response = new TransactionResponse("txn_abc", 123L, orderId, "APPROVED", 200L);

        idempotencyService.store(IDEMPOTENCY_KEY, response);

        verify(valueOps).set(eq(KEY), contains("txn_abc"), any());
    }

    @Test
    void getExisting_returns_empty_when_no_cached_value() {
        when(valueOps.get(KEY)).thenReturn(null);
        Optional<TransactionResponse> result = idempotencyService.getExisting(IDEMPOTENCY_KEY);
        assertThat(result).isEmpty();
    }

    @Test
    void getExisting_returns_deserialized_response_when_cached() {
        UUID orderId = UUID.randomUUID();
        String json = """
                {"transactionId":"txn_abc","mpPaymentId":123,"orderId":"%s","status":"APPROVED","processingTimeMs":200}
                """.formatted(orderId).trim();
        when(valueOps.get(KEY)).thenReturn(json);

        Optional<TransactionResponse> result = idempotencyService.getExisting(IDEMPOTENCY_KEY);

        assertThat(result).isPresent();
        assertThat(result.get().transactionId()).isEqualTo("txn_abc");
        assertThat(result.get().status()).isEqualTo("APPROVED");
    }

    @Test
    void getExisting_returns_empty_when_invalid_json() {
        when(valueOps.get(KEY)).thenReturn("invalid-json");
        Optional<TransactionResponse> result = idempotencyService.getExisting(IDEMPOTENCY_KEY);
        assertThat(result).isEmpty();
    }
}
