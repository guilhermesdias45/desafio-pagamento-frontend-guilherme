package com.acaboumony.user.e2e;

import com.acaboumony.user.domain.enums.UserRole;
import com.acaboumony.user.dto.request.LoginRequest;
import com.acaboumony.user.dto.request.RegisterRequest;
import com.acaboumony.user.support.BaseIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
class RefreshRotationFlowIT extends BaseIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired StringRedisTemplate redis;

    @Test
    void deve_rotacionar_refresh_token_e_invalidar_o_antigo() throws Exception {
        String email = "refresh-" + System.nanoTime() + "@test.com";

        // Register + confirm + login
        var regReq = new RegisterRequest(email, "Senha@1234", "Refresh User", UserRole.CUSTOMER, null, null);
        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(regReq)))
                .andExpect(status().isCreated());

        // Get confirm token from Redis and confirm
        Set<String> keys = redis.keys("email_confirm:*");
        assertThat(keys).isNotEmpty();
        String token = keys.iterator().next().substring("email_confirm:".length());
        mvc.perform(post("/api/v1/auth/confirm-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\"}"))
                .andExpect(status().isOk());

        // Login → get cookie refreshToken=A
        var loginReq = new LoginRequest(email, "Senha@1234", null, null);
        MvcResult loginResult = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andReturn();

        Cookie refreshCookieA = loginResult.getResponse().getCookie("refreshToken");
        assertThat(refreshCookieA).isNotNull();
        String tokenA = refreshCookieA.getValue();

        // Refresh with A → get B
        MvcResult refreshResult = mvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new Cookie("refreshToken", tokenA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andReturn();

        Cookie refreshCookieB = refreshResult.getResponse().getCookie("refreshToken");
        assertThat(refreshCookieB).isNotNull();
        String tokenB = refreshCookieB.getValue();
        assertThat(tokenB).isNotEqualTo(tokenA);

        // CE-REFRESH-001: using A again → 401 (token was rotated out)
        mvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new Cookie("refreshToken", tokenA)))
                .andExpect(status().isUnauthorized());

        // B still works
        mvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new Cookie("refreshToken", tokenB)))
                .andExpect(status().isOk());
    }
}
