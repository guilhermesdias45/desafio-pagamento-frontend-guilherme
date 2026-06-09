package com.acaboumony.payment.service;

import com.acaboumony.payment.domain.entity.MpTestAccount;
import com.acaboumony.payment.domain.enums.MpAccountType;
import com.acaboumony.payment.repository.MpTestAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MpTestAccountServiceTest {

    @Mock private MpTestAccountRepository repository;
    @Mock private MpEncryptionService encryptionService;
    @Mock private MpOAuthService oAuthService;

    private MpTestAccountService service;

    @BeforeEach
    void setUp() {
        service = new MpTestAccountService(repository, encryptionService, oAuthService);
    }

    @Test
    void getSellerAccount_returnsSeller() {
        var seller = new MpTestAccount(MpAccountType.SELLER, 3459882808L,
            "seller@test.com", null, null);
        when(repository.findByType(MpAccountType.SELLER)).thenReturn(Optional.of(seller));

        var result = service.getSellerAccount();

        assertTrue(result.isPresent());
        assertEquals(MpAccountType.SELLER, result.get().getType());
    }

    @Test
    void getSellerAccount_whenNoSeller_returnsEmpty() {
        when(repository.findByType(MpAccountType.SELLER)).thenReturn(Optional.empty());

        var result = service.getSellerAccount();

        assertTrue(result.isEmpty());
    }

    @Test
    void getSellerAccessToken_whenNoSeller_returnsEmpty() {
        when(repository.findByType(MpAccountType.SELLER)).thenReturn(Optional.empty());

        var result = service.getSellerAccessToken();

        assertTrue(result.isEmpty());
    }

    @Test
    void getSellerAccessToken_whenTokenEncNull_returnsEmpty() {
        var seller = new MpTestAccount(MpAccountType.SELLER, 3459882808L,
            "seller@test.com", null, null);
        seller.setAccessTokenEnc(null);
        when(repository.findByType(MpAccountType.SELLER)).thenReturn(Optional.of(seller));

        var result = service.getSellerAccessToken();

        assertTrue(result.isEmpty());
    }

    @Test
    void getSellerAccessToken_whenTokenValid_returnsDecryptedToken() {
        var seller = new MpTestAccount(MpAccountType.SELLER, 3459882808L,
            "seller@test.com", null, null);
        seller.setAccessTokenEnc("encrypted_token");
        seller.setTokenExpiresAt(Instant.now().plusSeconds(3600));
        when(repository.findByType(MpAccountType.SELLER)).thenReturn(Optional.of(seller));
        when(encryptionService.decrypt("encrypted_token")).thenReturn("APP_USR-valid-token");

        var result = service.getSellerAccessToken();

        assertTrue(result.isPresent());
        assertEquals("APP_USR-valid-token", result.get());
    }

    @Test
    void getSellerAccessToken_whenTokenExpired_refreshesToken() {
        var seller = new MpTestAccount(MpAccountType.SELLER, 3459882808L,
            "seller@test.com", null, null);
        seller.setAccessTokenEnc("encrypted_expired");
        seller.setRefreshTokenEnc("encrypted_refresh");
        seller.setTokenExpiresAt(Instant.now().minusSeconds(60));
        when(repository.findByType(MpAccountType.SELLER)).thenReturn(Optional.of(seller));
        when(encryptionService.decrypt("encrypted_refresh")).thenReturn("refresh_token_value");
        when(oAuthService.refreshAccessToken("refresh_token_value"))
            .thenReturn(new MpOAuthService.OAuthTokenResponse(
                "APP_USR-refreshed", "TG-new-refresh", null, Instant.now().plusSeconds(21600)));

        var result = service.getSellerAccessToken();

        assertTrue(result.isPresent());
        assertEquals("APP_USR-refreshed", result.get());
        verify(oAuthService).refreshAccessToken("refresh_token_value");
    }
}
