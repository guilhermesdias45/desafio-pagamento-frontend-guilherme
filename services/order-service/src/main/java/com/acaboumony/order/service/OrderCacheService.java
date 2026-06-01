package com.acaboumony.order.service;

import com.acaboumony.order.domain.entity.Order;
import com.acaboumony.order.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class OrderCacheService {

    private static final Logger log = LoggerFactory.getLogger(OrderCacheService.class);
    private static final String KEY_PREFIX = "order:";
    private static final long TTL_SECONDS = 60;

    private final StringRedisTemplate redisTemplate;
    private final OrderRepository orderRepository;

    public OrderCacheService(StringRedisTemplate redisTemplate, OrderRepository orderRepository) {
        this.redisTemplate = redisTemplate;
        this.orderRepository = orderRepository;
    }

    public Optional<Order> findById(UUID orderId) {
        var cached = redisTemplate.opsForValue().get(buildKey(orderId));
        if (cached != null) {
            log.debug("Cache hit for order {}", orderId);
            return Optional.of(UUID.fromString(cached))
                    .flatMap(orderRepository::findById);
        }

        log.debug("Cache miss for order {}", orderId);
        var order = orderRepository.findById(orderId);
        order.ifPresent(o ->
                redisTemplate.opsForValue().set(buildKey(orderId), orderId.toString(), TTL_SECONDS, TimeUnit.SECONDS)
        );
        return order;
    }

    public void evict(UUID orderId) {
        redisTemplate.delete(buildKey(orderId));
        log.debug("Cache evicted for order {}", orderId);
    }

    private String buildKey(UUID orderId) {
        return KEY_PREFIX + orderId;
    }
}

