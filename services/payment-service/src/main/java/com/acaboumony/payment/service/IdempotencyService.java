package com.acaboumony.payment.service;

import com.acaboumony.payment.dto.response.TransactionResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * Manages idempotency for payment transactions using Redis.
 * Key format: "pay:idem:{idempotencyKey}", TTL 24h.
 * Prevents duplicate charges on retried requests.
 */
@Service
public class IdempotencyService {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyService.class);
    private static final String KEY_PREFIX = "pay:idem:";
    private static final Duration TTL = Duration.ofHours(24);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public IdempotencyService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule());
    }

    public boolean isDuplicate(UUID idempotencyKey) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(KEY_PREFIX + idempotencyKey));
    }

    public void store(UUID idempotencyKey, TransactionResponse result) {
        try {
            String json = objectMapper.writeValueAsString(result);
            redisTemplate.opsForValue().set(KEY_PREFIX + idempotencyKey, json, TTL);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize TransactionResponse for idempotencyKey={}", idempotencyKey, e);
        }
    }

    public Optional<TransactionResponse> getExisting(UUID idempotencyKey) {
        String json = redisTemplate.opsForValue().get(KEY_PREFIX + idempotencyKey);
        if (json == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(json, TransactionResponse.class));
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize cached TransactionResponse for idempotencyKey={}", idempotencyKey, e);
            return Optional.empty();
        }
    }
}
