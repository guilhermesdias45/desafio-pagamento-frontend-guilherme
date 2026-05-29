package com.acaboumony.fraud.rules;

import com.acaboumony.fraud.dto.request.FraudAnalysisRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.quality.Strictness.LENIENT;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = LENIENT)
class UnusualHourRuleTest {

    @Mock
    private StringRedisTemplate redis;

    @Test
    void shouldReturnZeroDuringNormalHours() {
        var clock = Clock.fixed(Instant.parse("2026-05-29T14:00:00Z"), ZoneOffset.UTC);
        var rule = new UnusualHourRule(clock);
        var request = new FraudAnalysisRequest(
            "txn_001", UUID.randomUUID(), 50000L,
            "visa", "192.168.1.1", null, null, null
        );
        assertEquals(0, rule.evaluate(request, redis));
    }

    @Test
    void shouldReturnZeroDuringUnusualHourButLowAmount() {
        var clock = Clock.fixed(Instant.parse("2026-05-29T03:00:00Z"), ZoneOffset.UTC);
        var rule = new UnusualHourRule(clock);
        var request = new FraudAnalysisRequest(
            "txn_001", UUID.randomUUID(), 10000L,
            "visa", "192.168.1.1", null, null, null
        );
        assertEquals(0, rule.evaluate(request, redis));
    }

    @Test
    void shouldReturnZeroDuringUnusualHourButExactlyAtThreshold() {
        var clock = Clock.fixed(Instant.parse("2026-05-29T03:00:00Z"), ZoneOffset.UTC);
        var rule = new UnusualHourRule(clock);
        var request = new FraudAnalysisRequest(
            "txn_001", UUID.randomUUID(), 30000L,
            "visa", "192.168.1.1", null, null, null
        );
        assertEquals(0, rule.evaluate(request, redis));
    }

    @Test
    void shouldReturnTenDuringUnusualHourAndHighValue() {
        var clock = Clock.fixed(Instant.parse("2026-05-29T03:00:00Z"), ZoneOffset.UTC);
        var rule = new UnusualHourRule(clock);
        var request = new FraudAnalysisRequest(
            "txn_001", UUID.randomUUID(), 30001L,
            "visa", "192.168.1.1", null, null, null
        );
        assertEquals(10, rule.evaluate(request, redis));
    }

    @Test
    void shouldReturnTenAtTwoAm() {
        var clock = Clock.fixed(Instant.parse("2026-05-29T02:00:00Z"), ZoneOffset.UTC);
        var rule = new UnusualHourRule(clock);
        var request = new FraudAnalysisRequest(
            "txn_001", UUID.randomUUID(), 30001L,
            "visa", "192.168.1.1", null, null, null
        );
        assertEquals(10, rule.evaluate(request, redis));
    }

    @Test
    void shouldReturnTenAtFiveAm() {
        var clock = Clock.fixed(Instant.parse("2026-05-29T04:59:00Z"), ZoneOffset.UTC);
        var rule = new UnusualHourRule(clock);
        var request = new FraudAnalysisRequest(
            "txn_001", UUID.randomUUID(), 30001L,
            "visa", "192.168.1.1", null, null, null
        );
        assertEquals(10, rule.evaluate(request, redis));
    }

    @Test
    void shouldReturnCorrectReasonAndScore() {
        var clock = Clock.fixed(Instant.parse("2026-05-29T14:00:00Z"), ZoneOffset.UTC);
        var rule = new UnusualHourRule(clock);
        assertEquals("UNUSUAL_HOUR", rule.getReason());
        assertEquals(10, rule.getScore());
    }
}
