package com.acaboumony.user.security;

import com.acaboumony.user.config.JwtProperties;
import com.acaboumony.user.domain.enums.UserRole;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Date;
import java.util.Objects;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenValidatorTest {

    private static final String PRIVATE_KEY_B64;
    private static final String PUBLIC_KEY_B64;

    static {
        try (InputStream privIs = JwtTokenValidatorTest.class.getResourceAsStream("/test-keys/test-private-key.pem");
             InputStream pubIs  = JwtTokenValidatorTest.class.getResourceAsStream("/test-keys/test-public-key.pem")) {
            PRIVATE_KEY_B64 = new String(Objects.requireNonNull(privIs).readAllBytes(), StandardCharsets.UTF_8).strip();
            PUBLIC_KEY_B64  = new String(Objects.requireNonNull(pubIs).readAllBytes(), StandardCharsets.UTF_8).strip();
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private JwtTokenProvider provider;
    private JwtTokenValidator validator;

    @BeforeEach
    void setUp() {
        JwtProperties props = new JwtProperties(PRIVATE_KEY_B64, PUBLIC_KEY_B64, 900, 604800, 300);
        provider = new JwtTokenProvider(props);
        provider.init();
        validator = new JwtTokenValidator(props);
        validator.init();
    }

    @Test
    void deve_validar_token_e_retornar_claims_quando_token_assinado_corretamente() {
        UUID userId = UUID.randomUUID();
        String token = provider.generateAccessToken(userId, "valid@test.com", UserRole.CUSTOMER, null);

        JwtClaims claims = validator.validate(token);

        assertThat(claims.sub()).isEqualTo(userId);
        assertThat(claims.email()).isEqualTo("valid@test.com");
        assertThat(claims.role()).isEqualTo(UserRole.CUSTOMER);
        assertThat(claims.issuedAt()).isNotNull();
        assertThat(claims.expiresAt()).isNotNull();
    }

    @Test
    void deve_lancar_JwtValidationException_quando_assinatura_invalida() throws Exception {
        // Generate a second key pair and sign with the wrong private key
        var gen = java.security.KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        var wrongPair = gen.generateKeyPair();
        PrivateKey wrongPrivate = wrongPair.getPrivate();

        String tokenWithWrongKey = Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .claim("email", "x@y.com")
                .claim("role", "CUSTOMER")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 900_000))
                .signWith(wrongPrivate, Jwts.SIG.RS256)
                .compact();

        assertThatThrownBy(() -> validator.validate(tokenWithWrongKey))
                .isInstanceOf(JwtValidationException.class)
                .satisfies(ex -> assertThat(((JwtValidationException) ex).getErrorCode())
                        .isEqualTo("INVALID_SIGNATURE"));
    }

    @Test
    void deve_lancar_JwtValidationException_quando_token_expirado() {
        // Sign with exp = 1 second in the past
        PrivateKey privateKey = RsaKeyLoader.loadPrivateKey(PRIVATE_KEY_B64);
        String expiredToken = Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .claim("email", "exp@test.com")
                .claim("role", "CUSTOMER")
                .issuedAt(new Date(System.currentTimeMillis() - 2000))
                .expiration(new Date(System.currentTimeMillis() - 1000))
                .signWith(privateKey, Jwts.SIG.RS256)
                .compact();

        assertThatThrownBy(() -> validator.validate(expiredToken))
                .isInstanceOf(JwtValidationException.class)
                .satisfies(ex -> assertThat(((JwtValidationException) ex).getErrorCode())
                        .isEqualTo("REFRESH_TOKEN_EXPIRED"));
    }

    @Test
    void deve_lancar_JwtValidationException_quando_token_malformado() {
        assertThatThrownBy(() -> validator.validate("not.a.jwt"))
                .isInstanceOf(JwtValidationException.class)
                .satisfies(ex -> assertThat(((JwtValidationException) ex).getErrorCode())
                        .isIn("MALFORMED_TOKEN", "INVALID_TOKEN"));
    }

    @Test
    void deve_lancar_JwtValidationException_quando_claims_obrigatorios_ausentes() {
        // Token signed correctly but missing 'email' claim
        PrivateKey privateKey = RsaKeyLoader.loadPrivateKey(PRIVATE_KEY_B64);
        String tokenNoEmail = Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .claim("role", "CUSTOMER")
                // no "email" claim
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 900_000))
                .signWith(privateKey, Jwts.SIG.RS256)
                .compact();

        assertThatThrownBy(() -> validator.validate(tokenNoEmail))
                .isInstanceOf(JwtValidationException.class)
                .satisfies(ex -> assertThat(((JwtValidationException) ex).getErrorCode())
                        .isEqualTo("MISSING_CLAIMS"));
    }

    @SuppressWarnings("unused")
    private static java.security.KeyPairGenerator getInstance(String rsa) throws Exception {
        return java.security.KeyPairGenerator.getInstance(rsa);
    }
}
