package com.acaboumony.user.controller;

import com.acaboumony.user.domain.enums.UserRole;
import com.acaboumony.user.security.JwtTokenProvider;
import com.acaboumony.user.security.RsaKeyLoader;
import com.acaboumony.user.support.BaseIntegrationTest;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.util.Date;
import java.util.Objects;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class InternalAuthControllerIT extends BaseIntegrationTest {

    private static final String PRIVATE_KEY_B64;

    static {
        try (InputStream privIs = InternalAuthControllerIT.class.getResourceAsStream("/test-keys/test-private-key.pem")) {
            PRIVATE_KEY_B64 = new String(Objects.requireNonNull(privIs).readAllBytes(), StandardCharsets.UTF_8).strip();
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtTokenProvider tokenProvider;

    private static final String INTERNAL_SECRET = "test-internal-secret";

    @Test
    void deve_retornar_200_com_userId_email_role_merchantId_quando_JWT_valido_e_header_correto() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID merchantId = UUID.randomUUID();
        String token = tokenProvider.generateAccessToken(userId, "merchant@test.com", UserRole.MERCHANT_OWNER, merchantId);

        mockMvc.perform(post("/internal/auth/validate-token")
                        .header("X-Internal-Secret", INTERNAL_SECRET)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(userId.toString()))
                .andExpect(jsonPath("$.email").value("merchant@test.com"))
                .andExpect(jsonPath("$.role").value("MERCHANT_OWNER"))
                .andExpect(jsonPath("$.merchantId").value(merchantId.toString()));
    }

    @Test
    void deve_retornar_401_quando_JWT_expirado() throws Exception {
        PrivateKey privateKey = RsaKeyLoader.loadPrivateKey(PRIVATE_KEY_B64);
        String expiredToken = Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .claim("email", "exp@test.com")
                .claim("role", "CUSTOMER")
                .issuedAt(new Date(System.currentTimeMillis() - 2000))
                .expiration(new Date(System.currentTimeMillis() - 1000))
                .signWith(privateKey, Jwts.SIG.RS256)
                .compact();

        mockMvc.perform(post("/internal/auth/validate-token")
                        .header("X-Internal-Secret", INTERNAL_SECRET)
                        .header("Authorization", "Bearer " + expiredToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deve_retornar_403_quando_header_internal_secret_ausente() throws Exception {
        String token = tokenProvider.generateAccessToken(UUID.randomUUID(), "x@y.com", UserRole.CUSTOMER, null);
        mockMvc.perform(post("/internal/auth/validate-token")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void deve_retornar_403_quando_header_internal_secret_invalido() throws Exception {
        String token = tokenProvider.generateAccessToken(UUID.randomUUID(), "x@y.com", UserRole.CUSTOMER, null);
        mockMvc.perform(post("/internal/auth/validate-token")
                        .header("X-Internal-Secret", "wrong-secret")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void deve_retornar_401_quando_JWT_assinatura_invalida() throws Exception {
        var gen = java.security.KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        String tokenWrongKey = Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .claim("email", "x@y.com")
                .claim("role", "CUSTOMER")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 900_000))
                .signWith(gen.generateKeyPair().getPrivate(), Jwts.SIG.RS256)
                .compact();

        mockMvc.perform(post("/internal/auth/validate-token")
                        .header("X-Internal-Secret", INTERNAL_SECRET)
                        .header("Authorization", "Bearer " + tokenWrongKey))
                .andExpect(status().isUnauthorized());
    }
}
