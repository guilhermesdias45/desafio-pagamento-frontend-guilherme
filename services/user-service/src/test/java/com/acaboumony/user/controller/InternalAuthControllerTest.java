package com.acaboumony.user.controller;

import com.acaboumony.user.config.InternalSecretProperties;
import com.acaboumony.user.config.SecurityConfig;
import com.acaboumony.user.config.TestSecurityConfig;
import com.acaboumony.user.domain.enums.UserRole;
import com.acaboumony.user.security.JwtClaims;
import com.acaboumony.user.security.JwtTokenValidator;
import com.acaboumony.user.security.JwtValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = InternalAuthController.class,
        excludeAutoConfiguration = SecurityAutoConfiguration.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SecurityConfig.class)
)
@Import({GlobalExceptionHandler.class, TestSecurityConfig.class})
class InternalAuthControllerTest {

    static final String TEST_SECRET = "test-internal-secret";

    @Autowired MockMvc mvc;
    @MockBean JwtTokenValidator jwtTokenValidator;
    @MockBean InternalSecretProperties internalSecretProperties;
    @MockBean StringRedisTemplate stringRedisTemplate;

    @BeforeEach
    void setUp() {
        when(internalSecretProperties.secret()).thenReturn(TEST_SECRET);
    }

    @Test
    void deve_retornar_200_com_claims_quando_token_valido() throws Exception {
        UUID userId = UUID.randomUUID();
        JwtClaims claims = new JwtClaims(
                userId, "ana@loja.com.br", UserRole.CUSTOMER, null,
                Instant.now(), Instant.now().plusSeconds(900));

        when(jwtTokenValidator.validate("valid.jwt.token")).thenReturn(claims);

        mvc.perform(post("/internal/auth/validate-token")
                        .header("Authorization", "Bearer valid.jwt.token")
                        .header("X-Internal-Secret", TEST_SECRET))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(userId.toString()))
                .andExpect(jsonPath("$.email").value("ana@loja.com.br"))
                .andExpect(jsonPath("$.role").value("CUSTOMER"));
    }

    @Test
    void deve_retornar_200_com_merchantId_para_MERCHANT_OWNER() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID merchantId = UUID.randomUUID();
        JwtClaims claims = new JwtClaims(
                userId, "dono@loja.com.br", UserRole.MERCHANT_OWNER, merchantId,
                Instant.now(), Instant.now().plusSeconds(900));

        when(jwtTokenValidator.validate("merchant.jwt.token")).thenReturn(claims);

        mvc.perform(post("/internal/auth/validate-token")
                        .header("Authorization", "Bearer merchant.jwt.token")
                        .header("X-Internal-Secret", TEST_SECRET))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.merchantId").value(merchantId.toString()));
    }

    @Test
    void deve_retornar_401_quando_token_invalido() throws Exception {
        when(jwtTokenValidator.validate(eq("bad.jwt.token")))
                .thenThrow(new JwtValidationException("MALFORMED_TOKEN", "JWT malformed"));

        mvc.perform(post("/internal/auth/validate-token")
                        .header("Authorization", "Bearer bad.jwt.token")
                        .header("X-Internal-Secret", TEST_SECRET))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("MALFORMED_TOKEN"));
    }

    @Test
    void deve_retornar_401_quando_token_expirado() throws Exception {
        when(jwtTokenValidator.validate(eq("expired.jwt.token")))
                .thenThrow(new JwtValidationException("INVALID_TOKEN", "JWT expired"));

        mvc.perform(post("/internal/auth/validate-token")
                        .header("Authorization", "Bearer expired.jwt.token")
                        .header("X-Internal-Secret", TEST_SECRET))
                .andExpect(status().isUnauthorized());
    }
}
