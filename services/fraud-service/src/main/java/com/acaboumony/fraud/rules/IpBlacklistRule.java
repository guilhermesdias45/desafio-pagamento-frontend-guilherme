package com.acaboumony.fraud.rules;

import com.acaboumony.fraud.domain.entity.IpBlacklist;
import com.acaboumony.fraud.dto.request.FraudAnalysisRequest;
import com.acaboumony.fraud.repository.IpBlacklistRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.OffsetDateTime;
import java.util.concurrent.TimeUnit;

public class IpBlacklistRule implements FraudRule {

    private static final Logger log = LoggerFactory.getLogger(IpBlacklistRule.class);
    static final String KEY_PREFIX = "fraud:ip_blacklist:";

    private final IpBlacklistRepository ipBlacklistRepository;

    public IpBlacklistRule(IpBlacklistRepository ipBlacklistRepository) {
        this.ipBlacklistRepository = ipBlacklistRepository;
    }

    @Override
    public int evaluate(FraudAnalysisRequest request, StringRedisTemplate redis) {
        String key = KEY_PREFIX + request.ipAddress();
        Boolean blacklisted = redis.opsForSet().isMember(key, request.ipAddress());
        if (Boolean.TRUE.equals(blacklisted)) {
            return 40;
        }

        try {
            var dbEntry = ipBlacklistRepository.findByIpAddress(request.ipAddress());
            if (dbEntry.isPresent()) {
                IpBlacklist entry = dbEntry.get();
                if (entry.getExpiresAt() == null || entry.getExpiresAt().isAfter(OffsetDateTime.now())) {
                    redis.opsForSet().add(key, request.ipAddress());
                    redis.expire(key, 24, TimeUnit.HOURS);
                    return 40;
                }
            }
        } catch (Exception e) {
            log.warn("Database IP blacklist lookup failed: {}", e.getMessage());
        }

        return 0;
    }

    @Override
    public String getReason() {
        return "IP_BLACKLISTED";
    }

    @Override
    public int getScore() {
        return 40;
    }
}
