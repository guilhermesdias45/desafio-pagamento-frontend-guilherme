package com.acaboumony.notification.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EmailRateLimiterTest {

    @Test
    void shouldAllowFirstEmail() {
        var limiter = new EmailRateLimiter(10);
        assertThat(limiter.isRateLimited("test@test.com")).isFalse();
    }

    @Test
    void shouldBlockAfterMaxEmails() {
        var limiter = new EmailRateLimiter(2);

        assertThat(limiter.isRateLimited("test@test.com")).isFalse();
        assertThat(limiter.isRateLimited("test@test.com")).isFalse();
        assertThat(limiter.isRateLimited("test@test.com")).isTrue();
    }

    @Test
    void shouldTrackSeparateBuckets() {
        var limiter = new EmailRateLimiter(2);

        assertThat(limiter.isRateLimited("alice@test.com")).isFalse();
        assertThat(limiter.isRateLimited("bob@test.com")).isFalse();
        assertThat(limiter.isRateLimited("alice@test.com")).isFalse();
        assertThat(limiter.isRateLimited("bob@test.com")).isFalse();

        assertThat(limiter.isRateLimited("alice@test.com")).isTrue();
        assertThat(limiter.isRateLimited("bob@test.com")).isTrue();
    }

    @Test
    void shouldNotRateLimitNullRecipient() {
        var limiter = new EmailRateLimiter(1);
        assertThat(limiter.isRateLimited(null)).isFalse();
    }

    @Test
    void shouldNotRateLimitBlankRecipient() {
        var limiter = new EmailRateLimiter(1);
        assertThat(limiter.isRateLimited("")).isFalse();
        assertThat(limiter.isRateLimited("   ")).isFalse();
    }

    @Test
    void shouldAllowDifferentRecipientsIndependently() {
        var limiter = new EmailRateLimiter(1);

        assertThat(limiter.isRateLimited("a@test.com")).isFalse();
        assertThat(limiter.isRateLimited("b@test.com")).isFalse();

        assertThat(limiter.isRateLimited("a@test.com")).isTrue();
        assertThat(limiter.isRateLimited("b@test.com")).isTrue();
    }
}
