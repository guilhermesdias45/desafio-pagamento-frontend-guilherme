package com.acaboumony.user.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Binds {@code totp.*} properties from application.yml / application-docker.yml.
 *
 * <p>{@code aesKey} must be a 64-character lowercase hex string representing 32 bytes
 * (AES-256). {@code issuer} is displayed in the authenticator app (e.g. Google Authenticator).</p>
 */
@Validated
@ConfigurationProperties(prefix = "totp")
public record TotpProperties(
        @NotBlank @Size(min = 64, max = 64) String aesKey,
        @NotBlank String issuer
) {}
