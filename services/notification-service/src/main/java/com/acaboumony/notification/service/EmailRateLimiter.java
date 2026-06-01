package com.acaboumony.notification.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
public class EmailRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(EmailRateLimiter.class);
    private static final int CLEANUP_INTERVAL_MINUTES = 5;

    private final int maxEmailsPerHour;

    private final Map<String, EmailBucket> buckets = new ConcurrentHashMap<>();
    private final ScheduledExecutorService cleanupExecutor = Executors.newSingleThreadScheduledExecutor();

    public EmailRateLimiter(@Value("${notification.email.rate-limit:10}") int maxEmailsPerHour) {
        this.maxEmailsPerHour = maxEmailsPerHour;
    }

    @PostConstruct
    void startCleanup() {
        cleanupExecutor.scheduleAtFixedRate(this::cleanup, CLEANUP_INTERVAL_MINUTES, CLEANUP_INTERVAL_MINUTES, TimeUnit.MINUTES);
    }

    @PreDestroy
    void shutdown() {
        cleanupExecutor.shutdown();
    }

    public boolean isRateLimited(String recipientEmail) {
        if (recipientEmail == null || recipientEmail.isBlank()) {
            return false;
        }
        var now = Instant.now();
        var bucket = buckets.computeIfAbsent(recipientEmail, k -> new EmailBucket());
        synchronized (bucket) {
            bucket.removeExpired(now);
            if (bucket.count() >= maxEmailsPerHour) {
                log.warn("Rate limit reached for {}: {} emails in the last hour", recipientEmail, bucket.count());
                return true;
            }
            bucket.add(now);
            return false;
        }
    }

    private void cleanup() {
        var now = Instant.now();
        buckets.entrySet().removeIf(entry -> {
            var bucket = entry.getValue();
            synchronized (bucket) {
                bucket.removeExpired(now);
                return bucket.count() == 0;
            }
        });
    }

    static class EmailBucket {
        private static final long WINDOW_MILLIS = TimeUnit.HOURS.toMillis(1);

        private final java.util.LinkedList<Long> timestamps = new java.util.LinkedList<>();

        EmailBucket() {
        }

        int count() {
            return timestamps.size();
        }

        void add(Instant now) {
            timestamps.addLast(now.toEpochMilli());
        }

        void removeExpired(Instant now) {
            var cutoff = now.toEpochMilli() - WINDOW_MILLIS;
            while (!timestamps.isEmpty() && timestamps.peekFirst() < cutoff) {
                timestamps.removeFirst();
            }
        }
    }
}
