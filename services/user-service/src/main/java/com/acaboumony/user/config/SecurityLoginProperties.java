package com.acaboumony.user.config;

import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Binds {@code security.login.*} properties from application.yml.
 *
 * <ul>
 *   <li>{@code maxAttempts} — maximum failed login attempts before account is locked (default 5).</li>
 *   <li>{@code lockoutDurationMinutes} — lock duration in minutes (default 30).</li>
 * </ul>
 */
@Validated
@ConfigurationProperties(prefix = "security.login")
public record SecurityLoginProperties(
        @Positive int maxAttempts,
        @Positive int lockoutDurationMinutes
) {}
