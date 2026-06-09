package com.acaboumony.payment.controller;

import com.acaboumony.payment.service.MpOAuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(MpOAuthController.class)
class MpOAuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MpOAuthService oAuthService;

    @Test
    void authorize_redirectsToMp() throws Exception {
        when(oAuthService.generateAuthorizationUrl())
            .thenReturn("https://auth.mercadopago.com.br/authorization?client_id=test&response_type=code");

        mockMvc.perform(get("/api/v1/admin/mp-oauth/authorize"))
            .andExpect(status().is3xxRedirection())
            .andExpect(header().string("Location", "https://auth.mercadopago.com.br/authorization?client_id=test&response_type=code"));
    }

    @Test
    void callback_withValidCode_returnsOk() throws Exception {
        when(oAuthService.exchangeCode("valid_code"))
            .thenReturn(new MpOAuthService.OAuthTokenResponse(
                "APP_USR-token", "TG-refresh", "pub-key", Instant.now().plusSeconds(21600)));

        mockMvc.perform(get("/api/v1/admin/mp-oauth/callback")
                .param("code", "valid_code"))
            .andExpect(status().isOk())
            .andExpect(content().string("MercadoPago account linked successfully. You may close this tab."));
    }

    @Test
    void callback_whenExchangeFails_returns500() throws Exception {
        when(oAuthService.exchangeCode("bad_code"))
            .thenThrow(new RuntimeException("Invalid code"));

        mockMvc.perform(get("/api/v1/admin/mp-oauth/callback")
                .param("code", "bad_code"))
            .andExpect(status().isInternalServerError());
    }

    @Test
    void callback_withoutCode_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/admin/mp-oauth/callback"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void callback_acceptsOptionalState() throws Exception {
        when(oAuthService.exchangeCode("code123"))
            .thenReturn(new MpOAuthService.OAuthTokenResponse(
                "token", null, null, Instant.now().plusSeconds(21600)));

        mockMvc.perform(get("/api/v1/admin/mp-oauth/callback")
                .param("code", "code123")
                .param("state", "mystate"))
            .andExpect(status().isOk());
    }
}
