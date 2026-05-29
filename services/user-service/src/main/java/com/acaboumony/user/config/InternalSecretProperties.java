package com.acaboumony.user.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Binds {@code internal.*} properties from application.yml / application-docker.yml.
 *
 * <p>{@code secret} is the shared machine-to-machine secret used by the api-gateway
 * to authenticate calls to {@code /internal/**} endpoints via the
 * {@code X-Internal-Secret} request header. Compared in constant time to prevent
 * timing oracle attacks.</p>
 */
@Validated
@ConfigurationProperties(prefix = "internal")
public record InternalSecretProperties(
        @NotBlank String secret
) {}
