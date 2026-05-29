package com.acaboumony.user.config;

import com.acaboumony.user.UserServiceApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that all {@code @ConfigurationProperties} beans are correctly bound from
 * application properties at context startup.
 *
 * <p>Uses {@code @TestPropertySource} with dummy values to satisfy mandatory fields without
 * requiring Docker or real RSA keys.</p>
 */
@SpringBootTest(classes = UserServiceApplication.class)
@TestPropertySource(properties = {
        // JWT
        "jwt.private-key=test-private-key-base64-dummy",
        "jwt.public-key=test-public-key-base64-dummy",
        "jwt.access-token-expiration-seconds=900",
        "jwt.refresh-token-expiration-seconds=604800",
        "jwt.two-factor-token-expiration-seconds=300",
        // TOTP
        "totp.aes-key=0000000000000000000000000000000000000000000000000000000000000000",
        "totp.issuer=AcabouoMonyTest",
        // Internal
        "internal.secret=test-internal-secret-value",
        // Security login
        "security.login.max-attempts=5",
        "security.login.lockout-duration-minutes=30",
        // Disable auto-config that needs real infrastructure
        "spring.autoconfigure.exclude=" +
                "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration," +
                "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration," +
                "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration," +
                "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration," +
                "org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration",
        "spring.jpa.hibernate.ddl-auto=none"
})
class ConfigurationPropertiesTest {

    @Autowired
    private JwtProperties jwtProperties;

    @Autowired
    private TotpProperties totpProperties;

    @Autowired
    private InternalSecretProperties internalSecretProperties;

    @Test
    void deve_carregar_jwt_properties_com_chaves_e_expiracoes_quando_context_sobe() {
        assertThat(jwtProperties).isNotNull();
        assertThat(jwtProperties.privateKey()).isEqualTo("test-private-key-base64-dummy");
        assertThat(jwtProperties.publicKey()).isEqualTo("test-public-key-base64-dummy");
        assertThat(jwtProperties.accessTokenExpirationSeconds()).isEqualTo(900);
        assertThat(jwtProperties.refreshTokenExpirationSeconds()).isEqualTo(604800);
        assertThat(jwtProperties.twoFactorTokenExpirationSeconds()).isEqualTo(300);
    }

    @Test
    void deve_carregar_totp_properties_com_aes_key_e_issuer_quando_context_sobe() {
        assertThat(totpProperties).isNotNull();
        assertThat(totpProperties.aesKey())
                .isEqualTo("0000000000000000000000000000000000000000000000000000000000000000");
        assertThat(totpProperties.issuer()).isEqualTo("AcabouoMonyTest");
    }

    @Test
    void deve_carregar_internal_secret_properties_quando_context_sobe() {
        assertThat(internalSecretProperties).isNotNull();
        assertThat(internalSecretProperties.secret()).isEqualTo("test-internal-secret-value");
    }
}
