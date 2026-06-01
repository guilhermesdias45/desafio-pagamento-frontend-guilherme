package com.acaboumony.fraud.config;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RedisConfigTest {

    @Test
    void shouldCreateStringRedisTemplate() {
        RedisConfig config = new RedisConfig();
        RedisConnectionFactory factory = mock(RedisConnectionFactory.class);
        StringRedisTemplate template = config.stringRedisTemplate(factory);
        assertNotNull(template);
        assertSame(factory, template.getConnectionFactory());
    }
}
