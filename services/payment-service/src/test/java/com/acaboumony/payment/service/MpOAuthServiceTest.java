package com.acaboumony.payment.service;

import com.acaboumony.payment.config.MpOAuthConfig;
import com.acaboumony.payment.domain.entity.MpTestAccount;
import com.acaboumony.payment.domain.enums.MpAccountType;
import com.acaboumony.payment.repository.MpTestAccountRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MpOAuthServiceTest {

    @Mock private MpOAuthConfig config;
    @Mock private MpTestAccountRepository repository;
    @Mock private MpEncryptionService encryptionService;
    @Mock private RestTemplate restTemplate;

    @Test
    void generateAuthorizationUrl_containsRequiredParams() {
        when(config.getAuthUrl()).thenReturn("https://auth.mercadopago.com.br/authorization");
        when(config.getClientId()).thenReturn("test-client-id");
        when(config.getRedirectUri()).thenReturn("http://localhost:8082/api/v1/admin/mp-oauth/callback");
        var service = new MpOAuthService(config, repository, encryptionService, restTemplate);

        var url = service.generateAuthorizationUrl();
        assertTrue(url.startsWith("https://auth.mercadopago.com.br/authorization"));
        assertTrue(url.contains("client_id=test-client-id"));
        assertTrue(url.contains("response_type=code"));
        assertTrue(url.contains("redirect_uri=" + config.getRedirectUri()));
        assertTrue(url.contains("state="));
    }

    @Test
    void generateAuthorizationUrl_hasUniqueState() {
        when(config.getAuthUrl()).thenReturn("https://auth.mercadopago.com.br/authorization");
        when(config.getClientId()).thenReturn("test-client-id");
        when(config.getRedirectUri()).thenReturn("http://localhost:8082/api/v1/admin/mp-oauth/callback");
        var service = new MpOAuthService(config, repository, encryptionService, restTemplate);

        var url1 = service.generateAuthorizationUrl();
        var url2 = service.generateAuthorizationUrl();
        assertNotEquals(url1, url2);
    }

    @Test
    void exchangeCode_withValidCode_persistsTokens() {
        when(config.getClientId()).thenReturn("test-client-id");
        when(config.getClientSecret()).thenReturn("test-client-secret");
        when(config.getRedirectUri()).thenReturn("http://localhost:8082/api/v1/admin/mp-oauth/callback");
        when(config.getTokenUrl()).thenReturn("https://api.mercadopago.com/oauth/token");
        when(restTemplate.postForEntity(anyString(), any(), eq(Map.class)))
            .thenReturn(ResponseEntity.ok(Map.of(
                "access_token", "APP_USR-12345",
                "refresh_token", "TG-67890",
                "public_key", "APP_USR-pub-key",
                "expires_in", 21600
            )));
        var seller = new MpTestAccount(MpAccountType.SELLER, 3459882808L,
            "seller@test.com", null, null);
        when(repository.findByType(MpAccountType.SELLER)).thenReturn(Optional.of(seller));
        when(encryptionService.encrypt(anyString())).thenReturn("encrypted_value");
        when(repository.save(any())).thenReturn(seller);
        var service = new MpOAuthService(config, repository, encryptionService, restTemplate);

        var result = service.exchangeCode("valid_code");

        assertNotNull(result);
        assertEquals("APP_USR-12345", result.accessToken());
        assertEquals("TG-67890", result.refreshToken());
        assertEquals("APP_USR-pub-key", result.publicKey());
        assertNotNull(result.expiresAt());
        verify(repository).save(seller);
        verify(encryptionService).encrypt("APP_USR-12345");
        verify(encryptionService).encrypt("TG-67890");
    }

    @Test
    void exchangeCode_whenApiFails_throwsException() {
        when(config.getClientId()).thenReturn("test-client-id");
        when(config.getClientSecret()).thenReturn("test-client-secret");
        when(config.getRedirectUri()).thenReturn("http://localhost:8082/api/v1/admin/mp-oauth/callback");
        when(config.getTokenUrl()).thenReturn("https://api.mercadopago.com/oauth/token");
        when(restTemplate.postForEntity(anyString(), any(), eq(Map.class)))
            .thenThrow(new RuntimeException("Connection refused"));
        var service = new MpOAuthService(config, repository, encryptionService, restTemplate);

        assertThrows(RuntimeException.class, () -> service.exchangeCode("bad_code"));
    }

    @Test
    void refreshAccessToken_withValidToken_persistsNewTokens() {
        when(config.getClientId()).thenReturn("test-client-id");
        when(config.getClientSecret()).thenReturn("test-client-secret");
        when(config.getTokenUrl()).thenReturn("https://api.mercadopago.com/oauth/token");
        when(restTemplate.postForEntity(anyString(), any(), eq(Map.class)))
            .thenReturn(ResponseEntity.ok(Map.of(
                "access_token", "APP_USR-refreshed",
                "refresh_token", "TG-refreshed",
                "public_key", "APP_USR-pub-key-2",
                "expires_in", 21600
            )));
        var seller = new MpTestAccount(MpAccountType.SELLER, 3459882808L,
            "seller@test.com", null, null);
        when(repository.findByType(MpAccountType.SELLER)).thenReturn(Optional.of(seller));
        when(encryptionService.encrypt(anyString())).thenReturn("encrypted_value");
        when(repository.save(any())).thenReturn(seller);
        var service = new MpOAuthService(config, repository, encryptionService, restTemplate);

        var result = service.refreshAccessToken("TG-old-refresh");

        assertNotNull(result);
        assertEquals("APP_USR-refreshed", result.accessToken());
        verify(repository).save(seller);
    }

    @Test
    void exchangeCode_withoutSellerInDb_logsWarning() {
        when(config.getClientId()).thenReturn("test-client-id");
        when(config.getClientSecret()).thenReturn("test-client-secret");
        when(config.getRedirectUri()).thenReturn("http://localhost:8082/api/v1/admin/mp-oauth/callback");
        when(config.getTokenUrl()).thenReturn("https://api.mercadopago.com/oauth/token");
        when(restTemplate.postForEntity(anyString(), any(), eq(Map.class)))
            .thenReturn(ResponseEntity.ok(Map.of(
                "access_token", "APP_USR-12345",
                "expires_in", 21600
            )));
        when(repository.findByType(MpAccountType.SELLER)).thenReturn(Optional.empty());
        var service = new MpOAuthService(config, repository, encryptionService, restTemplate);

        var result = service.exchangeCode("valid_code");

        assertNotNull(result);
        assertEquals("APP_USR-12345", result.accessToken());
        verify(repository, never()).save(any());
    }

}
