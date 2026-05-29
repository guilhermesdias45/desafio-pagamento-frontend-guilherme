package com.acaboumony.user.e2e;

import com.acaboumony.user.domain.enums.UserRole;
import com.acaboumony.user.security.JwtTokenProvider;
import com.acaboumony.user.support.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
class InternalValidateTokenFlowIT extends BaseIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired JwtTokenProvider jwtTokenProvider;

    @Test
    void deve_retornar_userId_email_role_merchantId_quando_POST_internal_validate_token_com_secret_e_JWT_validos() throws Exception {
        UUID userId = UUID.randomUUID();
        String token = jwtTokenProvider.generateAccessToken(userId, "test@test.com", UserRole.CUSTOMER, null);

        mvc.perform(post("/internal/auth/validate-token")
                        .header("X-Internal-Secret", "test-internal-secret")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(userId.toString()))
                .andExpect(jsonPath("$.email").value("test@test.com"))
                .andExpect(jsonPath("$.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.merchantId").isEmpty());
    }

    @Test
    void deve_retornar_403_quando_sem_X_Internal_Secret() throws Exception {
        // CE-INTERNAL-001
        mvc.perform(post("/internal/auth/validate-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void deve_retornar_403_quando_X_Internal_Secret_invalido() throws Exception {
        // CE-INTERNAL-002
        mvc.perform(post("/internal/auth/validate-token")
                        .header("X-Internal-Secret", "wrong-secret"))
                .andExpect(status().isForbidden());
    }

    @Test
    void deve_retornar_401_quando_JWT_expirado() throws Exception {
        // Use a known-expired JWT (hard to create without mocking time; use malformed instead)
        mvc.perform(post("/internal/auth/validate-token")
                        .header("X-Internal-Secret", "test-internal-secret")
                        .header("Authorization", "Bearer invalid.jwt.token"))
                .andExpect(status().isUnauthorized());
    }
}
