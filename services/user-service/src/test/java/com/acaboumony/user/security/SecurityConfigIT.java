package com.acaboumony.user.security;

import com.acaboumony.user.support.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "jwt.private-key=${TEST_PRIVATE_KEY:}",
        "jwt.public-key=${TEST_PUBLIC_KEY:}"
})
class SecurityConfigIT extends BaseIntegrationTest {

    private static final String PRIVATE_KEY_B64;
    private static final String PUBLIC_KEY_B64;

    static {
        try (InputStream privIs = SecurityConfigIT.class.getResourceAsStream("/test-keys/test-private-key.pem");
             InputStream pubIs  = SecurityConfigIT.class.getResourceAsStream("/test-keys/test-public-key.pem")) {
            PRIVATE_KEY_B64 = new String(Objects.requireNonNull(privIs).readAllBytes(), StandardCharsets.UTF_8).strip();
            PUBLIC_KEY_B64  = new String(Objects.requireNonNull(pubIs).readAllBytes(), StandardCharsets.UTF_8).strip();
        } catch (Exception e) {
            throw new RuntimeException("Failed to load test keys", e);
        }
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    // ─── Public routes ────────────────────────────────────────────────────────

    @Test
    void deve_permitir_acesso_publico_a_auth_register() throws Exception {
        mvc.perform(post("/api/v1/auth/register")
                        .contentType("application/json")
                        .content("{}"))
                // 400 because of validation — not 401 (security allowed it through)
                .andExpect(status().isBadRequest());
    }

    @Test
    void deve_permitir_acesso_publico_a_auth_login() throws Exception {
        mvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deve_permitir_acesso_publico_a_auth_confirm_email() throws Exception {
        mvc.perform(post("/api/v1/auth/confirm-email")
                        .contentType("application/json")
                        .content("{\"token\":\"invalid-token\"}"))
                // 4xx from service, not 401 from security
                .andExpect(status().is4xxClientError());
    }

    @Test
    void deve_permitir_acesso_publico_a_auth_refresh() throws Exception {
        // No cookie → 401 from controller, not from security filter
        mvc.perform(post("/api/v1/auth/refresh"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deve_permitir_acesso_publico_a_auth_2fa_verify() throws Exception {
        mvc.perform(post("/api/v1/auth/2fa/verify")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deve_permitir_acesso_publico_a_auth_2fa_recovery() throws Exception {
        mvc.perform(post("/api/v1/auth/2fa/recovery")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deve_permitir_acesso_publico_a_actuator_health() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    // ─── Authenticated routes: no JWT → 401 ──────────────────────────────────

    @Test
    void deve_retornar_401_em_users_me_sem_JWT() throws Exception {
        mvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
    }

    @Test
    void deve_retornar_RFC_7807_problem_details_quando_401() throws Exception {
        mvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").exists())
                .andExpect(jsonPath("$.status").value(401));
    }

    // ─── Internal route: X-Internal-Secret ───────────────────────────────────

    @Test
    void deve_retornar_403_em_internal_validate_token_sem_X_Internal_Secret() throws Exception {
        mvc.perform(post("/internal/auth/validate-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void deve_retornar_403_em_internal_validate_token_com_X_Internal_Secret_invalido() throws Exception {
        mvc.perform(post("/internal/auth/validate-token")
                        .header("X-Internal-Secret", "wrong-secret"))
                .andExpect(status().isForbidden());
    }

    @Test
    void deve_permitir_internal_validate_token_com_secret_valido_e_JWT_valido() throws Exception {
        String token = jwtTokenProvider.generateAccessToken(
                java.util.UUID.randomUUID(), "test@test.com",
                com.acaboumony.user.domain.enums.UserRole.CUSTOMER, null);

        mvc.perform(post("/internal/auth/validate-token")
                        .header("X-Internal-Secret", "test-internal-secret")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("test@test.com"));
    }
}
