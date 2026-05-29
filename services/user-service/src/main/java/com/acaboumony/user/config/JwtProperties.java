package com.acaboumony.user.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Binds {@code jwt.*} properties from application.yml / application-docker.yml.
 *
 * <p>{@code privateKey} and {@code publicKey} contain the base64-encoded PEM content
 * (with literal {@code \n} for line breaks). {@link com.acaboumony.user.security.RsaKeyLoader}
 * decodes them to {@link java.security.PrivateKey} / {@link java.security.PublicKey}.</p>
 */
@Validated
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(
        @NotBlank String privateKey,
        @NotBlank String publicKey,
        @Positive int accessTokenExpirationSeconds,
        @Positive int refreshTokenExpirationSeconds,
        @Positive int twoFactorTokenExpirationSeconds
) {}
