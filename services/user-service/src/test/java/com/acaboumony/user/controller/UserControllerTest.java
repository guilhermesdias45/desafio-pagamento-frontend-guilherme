package com.acaboumony.user.controller;

import com.acaboumony.user.domain.enums.UserRole;
import com.acaboumony.user.dto.request.UpdateProfileRequest;
import com.acaboumony.user.dto.response.UserProfileResponse;
import com.acaboumony.user.security.JwtAuthenticationToken;
import com.acaboumony.user.security.JwtClaims;
import com.acaboumony.user.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        controllers = UserController.class,
        excludeAutoConfiguration = SecurityAutoConfiguration.class
)
@Import(GlobalExceptionHandler.class)
class UserControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @MockBean UserService userService;

    private JwtAuthenticationToken mockJwt(UUID userId, UserRole role, UUID merchantId) {
        JwtClaims claims = new JwtClaims(userId, "user@test.com", role, merchantId,
                Instant.now(), Instant.now().plusSeconds(900));
        return new JwtAuthenticationToken(claims);
    }

    @Test
    void deve_retornar_200_com_perfil_quando_GET_me_com_JWT_valido() throws Exception {
        UUID userId = UUID.randomUUID();
        var profile = new UserProfileResponse(userId, "user@test.com", "Ana Lima",
                "CUSTOMER", null, false, Instant.now());
        when(userService.getProfile(userId)).thenReturn(profile);

        mvc.perform(get("/api/v1/users/me")
                        .with(authentication(mockJwt(userId, UserRole.CUSTOMER, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("user@test.com"))
                .andExpect(jsonPath("$.fullName").value("Ana Lima"))
                .andExpect(jsonPath("$.merchantId").isEmpty());
    }

    @Test
    void deve_retornar_perfil_com_merchantId_quando_user_e_MERCHANT_OWNER() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID merchantId = UUID.randomUUID();
        var profile = new UserProfileResponse(userId, "owner@loja.com.br", "Maria Merchant",
                "MERCHANT_OWNER", merchantId, false, Instant.now());
        when(userService.getProfile(userId)).thenReturn(profile);

        mvc.perform(get("/api/v1/users/me")
                        .with(authentication(mockJwt(userId, UserRole.MERCHANT_OWNER, merchantId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.merchantId").value(merchantId.toString()));
    }

    @Test
    void deve_retornar_perfil_com_merchantId_null_quando_user_e_CUSTOMER() throws Exception {
        UUID userId = UUID.randomUUID();
        var profile = new UserProfileResponse(userId, "user@test.com", "Carlos Customer",
                "CUSTOMER", null, false, Instant.now());
        when(userService.getProfile(userId)).thenReturn(profile);

        mvc.perform(get("/api/v1/users/me")
                        .with(authentication(mockJwt(userId, UserRole.CUSTOMER, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.merchantId").isEmpty());
    }

    @Test
    void deve_retornar_200_e_atualizar_fullName_quando_PATCH_me_com_payload_valido() throws Exception {
        UUID userId = UUID.randomUUID();
        var req = new UpdateProfileRequest("Novo Nome");
        var updated = new UserProfileResponse(userId, "user@test.com", "Novo Nome",
                "CUSTOMER", null, false, Instant.now());
        when(userService.updateFullName(eq(userId), eq("Novo Nome"))).thenReturn(updated);

        mvc.perform(patch("/api/v1/users/me")
                        .with(authentication(mockJwt(userId, UserRole.CUSTOMER, null)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Novo Nome"));
    }

    @Test
    void deve_retornar_400_quando_PATCH_me_com_fullName_blank() throws Exception {
        UUID userId = UUID.randomUUID();
        var req = new UpdateProfileRequest("");

        mvc.perform(patch("/api/v1/users/me")
                        .with(authentication(mockJwt(userId, UserRole.CUSTOMER, null)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    @Test
    void deve_retornar_400_quando_PATCH_me_com_fullName_acima_de_100_chars() throws Exception {
        UUID userId = UUID.randomUUID();
        var req = new UpdateProfileRequest("A".repeat(101));

        mvc.perform(patch("/api/v1/users/me")
                        .with(authentication(mockJwt(userId, UserRole.CUSTOMER, null)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    @Test
    void deve_retornar_perfil_sem_passwordHash_nem_totpSecretEncrypted() throws Exception {
        UUID userId = UUID.randomUUID();
        var profile = new UserProfileResponse(userId, "user@test.com", "Ana Lima",
                "CUSTOMER", null, false, Instant.now());
        when(userService.getProfile(any())).thenReturn(profile);

        String body = mvc.perform(get("/api/v1/users/me")
                        .with(authentication(mockJwt(userId, UserRole.CUSTOMER, null))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assert !body.contains("passwordHash");
        assert !body.contains("totpSecretEncrypted");
    }
}
