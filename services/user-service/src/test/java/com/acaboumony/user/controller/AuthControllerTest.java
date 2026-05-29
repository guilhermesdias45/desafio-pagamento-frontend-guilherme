package com.acaboumony.user.controller;

import com.acaboumony.user.dto.request.LoginRequest;
import com.acaboumony.user.dto.request.RegisterRequest;
import com.acaboumony.user.dto.response.RefreshResponse;
import com.acaboumony.user.dto.response.RegisterResponse;
import com.acaboumony.user.domain.enums.UserRole;
import com.acaboumony.user.exception.AccountLockedException;
import com.acaboumony.user.exception.AccountNotConfirmedException;
import com.acaboumony.user.exception.EmailAlreadyExistsException;
import com.acaboumony.user.exception.InvalidCredentialsException;
import com.acaboumony.user.exception.MissingMerchantDataException;
import com.acaboumony.user.exception.InvalidRoleException;
import com.acaboumony.user.exception.RefreshTokenInvalidException;
import com.acaboumony.user.result.AuthResult;
import com.acaboumony.user.security.JwtAuthenticationToken;
import com.acaboumony.user.security.JwtClaims;
import com.acaboumony.user.service.AuthService;
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

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        controllers = AuthController.class,
        excludeAutoConfiguration = SecurityAutoConfiguration.class
)
@Import(GlobalExceptionHandler.class)
class AuthControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @MockBean AuthService authService;

    // ─── Register ────────────────────────────────────────────────────────────

    @Test
    void deve_retornar_201_com_RegisterResponse_quando_POST_register_payload_valido() throws Exception {
        var req = new RegisterRequest("ana@loja.com.br", "Senha@1234", "Ana Lima",
                UserRole.CUSTOMER, null, null);
        var resp = new RegisterResponse(UUID.randomUUID(), "ana@loja.com.br", "CUSTOMER", null, false);
        when(authService.register(any())).thenReturn(resp);

        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("ana@loja.com.br"))
                .andExpect(jsonPath("$.role").value("CUSTOMER"));
    }

    @Test
    void deve_retornar_400_problem_details_quando_POST_register_payload_invalido_email_blank() throws Exception {
        var req = new RegisterRequest("", "Senha@1234", "Ana Lima", UserRole.CUSTOMER, null, null);

        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    @Test
    void deve_retornar_409_EMAIL_ALREADY_EXISTS_quando_AuthService_lanca_EmailAlreadyExistsException() throws Exception {
        var req = new RegisterRequest("dup@loja.com.br", "Senha@1234", "Ana Lima",
                UserRole.CUSTOMER, null, null);
        when(authService.register(any())).thenThrow(new EmailAlreadyExistsException());

        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("EMAIL_ALREADY_EXISTS"));
    }

    @Test
    void deve_retornar_400_MISSING_MERCHANT_DATA_quando_MERCHANT_OWNER_sem_cnpj() throws Exception {
        var req = new RegisterRequest("owner@loja.com.br", "Senha@1234", "Ana Lima",
                UserRole.MERCHANT_OWNER, null, null);
        when(authService.register(any())).thenThrow(new MissingMerchantDataException());

        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("MISSING_MERCHANT_DATA"));
    }

    @Test
    void deve_retornar_400_INVALID_ROLE_quando_role_STAFF() throws Exception {
        var req = new RegisterRequest("staff@loja.com.br", "Senha@1234", "Ana Lima",
                UserRole.STAFF, null, null);
        when(authService.register(any())).thenThrow(new InvalidRoleException());

        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_ROLE"));
    }

    // ─── Login ────────────────────────────────────────────────────────────────

    @Test
    void deve_retornar_200_e_setar_httpOnly_cookie_refreshToken_quando_POST_login_sucesso() throws Exception {
        var loginReq = new LoginRequest("ana@loja.com.br", "Senha@1234", null, null);
        when(authService.authenticate(any())).thenReturn(
                new AuthResult.Success("access.token.jwt", "Bearer", 900, false, "refresh-uuid"));

        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andExpect(header().string("Set-Cookie", containsString("refreshToken=refresh-uuid")))
                .andExpect(header().string("Set-Cookie", containsString("HttpOnly")))
                .andExpect(header().string("Set-Cookie", containsString("Secure")));
    }

    @Test
    void deve_NAO_incluir_refreshToken_no_body_da_resposta_login() throws Exception {
        var loginReq = new LoginRequest("ana@loja.com.br", "Senha@1234", null, null);
        when(authService.authenticate(any())).thenReturn(
                new AuthResult.Success("access.token.jwt", "Bearer", 900, false, "refresh-uuid"));

        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("refresh-uuid"))));
    }

    @Test
    void deve_retornar_200_com_RequiresTwoFactor_quando_login_com_2FA_ativo_sem_code() throws Exception {
        var loginReq = new LoginRequest("ana@loja.com.br", "Senha@1234", null, null);
        when(authService.authenticate(any())).thenReturn(
                new AuthResult.RequiresTwoFactor(true, "2fa-token-uuid"));

        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requiresTwoFactor").value(true))
                .andExpect(jsonPath("$.twoFactorToken").value("2fa-token-uuid"));
    }

    @Test
    void deve_retornar_401_INVALID_CREDENTIALS_quando_AuthService_retorna_Failure() throws Exception {
        var loginReq = new LoginRequest("ana@loja.com.br", "wrong", null, null);
        when(authService.authenticate(any())).thenReturn(
                new AuthResult.Failure("INVALID_CREDENTIALS", "Invalid email or password", false, null));

        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(loginReq)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_CREDENTIALS"));
    }

    @Test
    void deve_retornar_423_ACCOUNT_LOCKED_com_unlockAt_no_problem_details() throws Exception {
        var loginReq = new LoginRequest("ana@loja.com.br", "wrong", null, null);
        Instant unlockAt = Instant.now().plusSeconds(1800);
        when(authService.authenticate(any())).thenReturn(
                new AuthResult.Failure("ACCOUNT_LOCKED", "Account locked", false, unlockAt));

        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(loginReq)))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.errorCode").value("ACCOUNT_LOCKED"))
                .andExpect(jsonPath("$.unlockAt").exists());
    }

    @Test
    void deve_retornar_403_ACCOUNT_NOT_CONFIRMED_quando_status_PENDING() throws Exception {
        var loginReq = new LoginRequest("ana@loja.com.br", "Senha@1234", null, null);
        when(authService.authenticate(any())).thenReturn(
                new AuthResult.Failure("ACCOUNT_NOT_CONFIRMED", "Email not confirmed", false, null));

        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(loginReq)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCOUNT_NOT_CONFIRMED"));
    }

    // ─── Refresh ──────────────────────────────────────────────────────────────

    @Test
    void deve_retornar_200_e_novo_cookie_quando_POST_refresh_com_token_valido() throws Exception {
        when(authService.refresh("old-token")).thenReturn(
                new RefreshResponse("new.access.jwt", "Bearer", 900, "new-refresh-uuid"));

        mvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new jakarta.servlet.http.Cookie("refreshToken", "old-token")))
                .andExpect(status().isOk())
                .andExpect(header().string("Set-Cookie", containsString("refreshToken=new-refresh-uuid")))
                .andExpect(jsonPath("$.accessToken").value("new.access.jwt"));
    }

    @Test
    void deve_retornar_401_quando_POST_refresh_sem_cookie() throws Exception {
        mvc.perform(post("/api/v1/auth/refresh"))
                .andExpect(status().isUnauthorized());
    }

    // ─── Logout ───────────────────────────────────────────────────────────────

    @Test
    void deve_retornar_204_quando_POST_logout() throws Exception {
        UUID userId = UUID.randomUUID();
        JwtClaims claims = new JwtClaims(userId, "ana@loja.com.br",
                com.acaboumony.user.domain.enums.UserRole.CUSTOMER, null,
                Instant.now(), Instant.now().plusSeconds(900));

        mvc.perform(post("/api/v1/auth/logout")
                        .with(authentication(new JwtAuthenticationToken(claims)))
                        .cookie(new jakarta.servlet.http.Cookie("refreshToken", "some-token")))
                .andExpect(status().isNoContent());
    }

    // ─── Resend confirmation ──────────────────────────────────────────────────

    @Test
    void deve_retornar_501_NOT_IMPLEMENTED_SPRINT_2_quando_POST_resend_confirmation() throws Exception {
        mvc.perform(post("/api/v1/auth/resend-confirmation"))
                .andExpect(status().isNotImplemented())
                .andExpect(jsonPath("$.errorCode").value("NOT_IMPLEMENTED_SPRINT_2"));
    }
}
