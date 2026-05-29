package com.acaboumony.user.security;

import com.acaboumony.user.config.JwtProperties;
import com.acaboumony.user.domain.enums.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.util.Date;
import java.util.Objects;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class JwtTokenProviderTest {

    private static final String PRIVATE_KEY_B64;
    private static final String PUBLIC_KEY_B64;

    static {
        try (InputStream privIs = JwtTokenProviderTest.class.getResourceAsStream("/test-keys/test-private-key.pem");
             InputStream pubIs  = JwtTokenProviderTest.class.getResourceAsStream("/test-keys/test-public-key.pem")) {
            PRIVATE_KEY_B64 = new String(Objects.requireNonNull(privIs).readAllBytes(), StandardCharsets.UTF_8).strip();
            PUBLIC_KEY_B64  = new String(Objects.requireNonNull(pubIs).readAllBytes(), StandardCharsets.UTF_8).strip();
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private JwtTokenProvider provider;
    private PublicKey publicKey;

    @BeforeEach
    void setUp() {
        JwtProperties props = new JwtProperties(
                PRIVATE_KEY_B64, PUBLIC_KEY_B64,
                900, 604800, 300);
        provider = new JwtTokenProvider(props);
        provider.init();
        publicKey = RsaKeyLoader.loadPublicKey(PUBLIC_KEY_B64);
    }

    @Test
    void deve_gerar_token_rs256_com_claims_corretos_quando_generateAccessToken_chamado() {
        UUID userId = UUID.randomUUID();
        String email = "test@example.com";
        UserRole role = UserRole.CUSTOMER;

        String token = provider.generateAccessToken(userId, email, role, null);

        Claims claims = Jwts.parser().verifyWith(publicKey).build()
                .parseSignedClaims(token).getPayload();

        assertThat(claims.getSubject()).isEqualTo(userId.toString());
        assertThat(claims.get("email", String.class)).isEqualTo(email);
        assertThat(claims.get("role", String.class)).isEqualTo("CUSTOMER");
        assertThat(claims.getIssuedAt()).isNotNull();
        assertThat(claims.getExpiration()).isNotNull();
    }

    @Test
    void deve_setar_exp_em_15_minutos_a_partir_de_iat_quando_generateAccessToken() {
        String token = provider.generateAccessToken(UUID.randomUUID(), "a@b.com", UserRole.CUSTOMER, null);

        Claims claims = Jwts.parser().verifyWith(publicKey).build()
                .parseSignedClaims(token).getPayload();

        long diffSeconds = (claims.getExpiration().getTime() - claims.getIssuedAt().getTime()) / 1000L;
        // 900 seconds ± 2 second tolerance
        assertThat(diffSeconds).isCloseTo(900L, within(2L));
    }

    @Test
    void deve_incluir_merchantId_quando_role_e_MERCHANT_OWNER() {
        UUID merchantId = UUID.randomUUID();
        String token = provider.generateAccessToken(
                UUID.randomUUID(), "merchant@example.com", UserRole.MERCHANT_OWNER, merchantId);

        Claims claims = Jwts.parser().verifyWith(publicKey).build()
                .parseSignedClaims(token).getPayload();

        assertThat(claims.get("merchantId", String.class)).isEqualTo(merchantId.toString());
    }

    @Test
    void deve_setar_merchantId_null_quando_role_e_CUSTOMER() {
        String token = provider.generateAccessToken(
                UUID.randomUUID(), "customer@example.com", UserRole.CUSTOMER, null);

        Claims claims = Jwts.parser().verifyWith(publicKey).build()
                .parseSignedClaims(token).getPayload();

        // null merchantId may be absent from the JSON or present with null value
        Object merchantId = claims.get("merchantId");
        assertThat(merchantId).isNull();
    }

    @Test
    void token_gerado_deve_ser_validado_com_sucesso_pelo_validator_round_trip() {
        JwtProperties props = new JwtProperties(
                PRIVATE_KEY_B64, PUBLIC_KEY_B64, 900, 604800, 300);
        JwtTokenValidator validator = new JwtTokenValidator(props);
        validator.init();

        UUID userId = UUID.randomUUID();
        String token = provider.generateAccessToken(userId, "x@y.com", UserRole.STAFF, null);
        JwtClaims parsed = validator.validate(token);

        assertThat(parsed.sub()).isEqualTo(userId);
        assertThat(parsed.email()).isEqualTo("x@y.com");
        assertThat(parsed.role()).isEqualTo(UserRole.STAFF);
    }
}
