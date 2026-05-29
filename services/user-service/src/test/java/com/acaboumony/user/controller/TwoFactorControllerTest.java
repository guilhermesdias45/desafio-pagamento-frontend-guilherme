package com.acaboumony.user.controller;

import com.acaboumony.user.domain.enums.UserRole;
import com.acaboumony.user.dto.request.RecoveryCodeRequest;
import com.acaboumony.user.dto.request.TwoFactorConfirmRequest;
import com.acaboumony.user.dto.request.TwoFactorDisableRequest;
import com.acaboumony.user.dto.request.TwoFactorVerifyRequest;
import com.acaboumony.user.dto.response.TwoFactorSetupResponse;
import com.acaboumony.user.exception.InvalidTotpCodeException;
import com.acaboumony.user.exception.RecoveryCodeExhaustedException;
import com.acaboumony.user.exception.RecoveryCodeInvalidException;
import com.acaboumony.user.exception.TwoFactorAlreadyEnabledException;
import com.acaboumony.user.result.AuthResult;
import com.acaboumony.user.security.JwtAuthenticationToken;
import com.acaboumony.user.security.JwtClaims;
import com.acaboumony.user.service.TwoFactorService;
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
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        controllers = TwoFactorController.class,
        excludeAutoConfiguration = SecurityAutoConfiguration.class
)
@Import(GlobalExceptionHandler.class)
class TwoFactorControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @MockBean TwoFactorService twoFactorService;

    private JwtAuthenticationToken mockJwt() {
        UUID userId = UUID.randomUUID();
        JwtClaims claims = new JwtClaims(userId, "user@test.com", UserRole.CUSTOMER, null,
                Instant.now(), Instant.now().plusSeconds(900));
        return new JwtAuthenticationToken(claims);
    }

    @Test
    void deve_retornar_200_com_secret_qrCode_e_recoveryCodes_quando_POST_2fa_setup_autenticado() throws Exception {
        var resp = new TwoFactorSetupResponse("SECRET32", "data:image/png;base64,AAA",
                "otpauth://totp/...", List.of("CODE1-CODE", "CODE2-CODE", "CODE3-CODE",
                "CODE4-CODE", "CODE5-CODE", "CODE6-CODE", "CODE7-CODE", "CODE8-CODE"));
        when(twoFactorService.setup(any())).thenReturn(resp);

        mvc.perform(post("/api/v1/auth/2fa/setup")
                        .with(authentication(mockJwt())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.secret").value("SECRET32"))
                .andExpect(jsonPath("$.recoveryCodes").isArray());
    }

    @Test
    void deve_retornar_409_TWO_FACTOR_ALREADY_ENABLED_quando_setup_em_user_com_2FA_ativo() throws Exception {
        when(twoFactorService.setup(any())).thenThrow(new TwoFactorAlreadyEnabledException());

        mvc.perform(post("/api/v1/auth/2fa/setup")
                        .with(authentication(mockJwt())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("TWO_FACTOR_ALREADY_ENABLED"));
    }

    @Test
    void deve_retornar_200_quando_POST_2fa_confirm_com_code_valido() throws Exception {
        var req = new TwoFactorConfirmRequest("123456");

        mvc.perform(post("/api/v1/auth/2fa/confirm")
                        .with(authentication(mockJwt()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isOk());
    }

    @Test
    void deve_retornar_401_INVALID_TOTP_CODE_quando_confirm_com_code_invalido() throws Exception {
        var req = new TwoFactorConfirmRequest("000000");
        doThrow(new InvalidTotpCodeException()).when(twoFactorService).confirm(any(), eq("000000"));

        mvc.perform(post("/api/v1/auth/2fa/confirm")
                        .with(authentication(mockJwt()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_TOTP_CODE"));
    }

    @Test
    void deve_retornar_200_com_AuthResult_Success_quando_POST_2fa_verify_com_twoFactorToken_e_code_validos() throws Exception {
        var req = new TwoFactorVerifyRequest("2fa-token", "123456");
        when(twoFactorService.verifyTwoFactorToken("2fa-token", "123456"))
                .thenReturn(new AuthResult.Success("access.jwt", "Bearer", 900, false, "refresh-uuid"));

        mvc.perform(post("/api/v1/auth/2fa/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(header().string("Set-Cookie", containsString("refreshToken=refresh-uuid")))
                .andExpect(jsonPath("$.accessToken").value("access.jwt"));
    }

    @Test
    void deve_retornar_401_INVALID_TOTP_CODE_quando_verify_com_code_invalido() throws Exception {
        var req = new TwoFactorVerifyRequest("2fa-token", "000000");
        when(twoFactorService.verifyTwoFactorToken("2fa-token", "000000"))
                .thenReturn(new AuthResult.Failure("INVALID_TOTP_CODE", "Invalid code", false, null));

        mvc.perform(post("/api/v1/auth/2fa/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_TOTP_CODE"));
    }

    @Test
    void deve_retornar_204_quando_POST_2fa_disable_com_senha_e_code_validos() throws Exception {
        var req = new TwoFactorDisableRequest("Senha@1234", "123456");

        mvc.perform(post("/api/v1/auth/2fa/disable")
                        .with(authentication(mockJwt()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isNoContent());
    }

    @Test
    void deve_retornar_401_quando_disable_com_senha_errada() throws Exception {
        var req = new TwoFactorDisableRequest("wrong-pass", "123456");
        doThrow(new InvalidTotpCodeException()).when(twoFactorService).disable(any(), eq("wrong-pass"), any());

        mvc.perform(post("/api/v1/auth/2fa/disable")
                        .with(authentication(mockJwt()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_TOTP_CODE"));
    }

    @Test
    void deve_retornar_200_com_AuthResult_Success_quando_POST_2fa_recovery_com_code_valido() throws Exception {
        var req = new RecoveryCodeRequest("2fa-token", "ABCD-EFGH-IJKL-MNOP");
        when(twoFactorService.useRecoveryCodeAndLogin("2fa-token", "ABCD-EFGH-IJKL-MNOP"))
                .thenReturn(new AuthResult.Success("access.jwt", "Bearer", 900, false, "refresh-uuid"));

        mvc.perform(post("/api/v1/auth/2fa/recovery")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access.jwt"));
    }

    @Test
    void deve_retornar_401_RECOVERY_CODE_INVALID_quando_recovery_com_code_invalido() throws Exception {
        var req = new RecoveryCodeRequest("2fa-token", "WRONG-CODE-AAAA-BBBB");
        when(twoFactorService.useRecoveryCodeAndLogin(any(), any()))
                .thenThrow(new RecoveryCodeInvalidException());

        mvc.perform(post("/api/v1/auth/2fa/recovery")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("RECOVERY_CODE_INVALID"));
    }

    @Test
    void deve_retornar_422_RECOVERY_CODE_EXHAUSTED_quando_todos_recovery_codes_usados() throws Exception {
        var req = new RecoveryCodeRequest("2fa-token", "ABCD-EFGH-IJKL-MNOP");
        when(twoFactorService.useRecoveryCodeAndLogin(any(), any()))
                .thenThrow(new RecoveryCodeExhaustedException());

        mvc.perform(post("/api/v1/auth/2fa/recovery")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("RECOVERY_CODE_EXHAUSTED"));
    }
}
