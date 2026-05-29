package com.acaboumony.user.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

/**
 * Enables all {@code @ConfigurationProperties} beans used by the user-service.
 *
 * <p>Validation of bound values ({@code @NotBlank}, {@code @Size}, etc.) is enforced at
 * context startup via {@code @Validated} on each properties record.</p>
 */
@Configuration
@Validated
@EnableConfigurationProperties({
        JwtProperties.class,
        TotpProperties.class,
        InternalSecretProperties.class,
        SecurityLoginProperties.class
})
public class JwtConfig {
}
