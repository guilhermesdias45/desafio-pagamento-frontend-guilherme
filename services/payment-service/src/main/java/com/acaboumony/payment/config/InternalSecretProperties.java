package com.acaboumony.payment.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Binds {@code internal.*} properties from application.yml.
 *
 * <p>The {@code secret} is the shared machine-to-machine secret used when calling
 * other services' internal endpoints via the {@code X-Internal-Secret} request header.</p>
 */
@Validated
@ConfigurationProperties(prefix = "internal")
public record InternalSecretProperties(
        @NotBlank String secret
) {}
