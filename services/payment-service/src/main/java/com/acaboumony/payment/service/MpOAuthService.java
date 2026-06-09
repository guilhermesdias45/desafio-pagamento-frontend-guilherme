package com.acaboumony.payment.service;

import com.acaboumony.payment.config.MpOAuthConfig;
import com.acaboumony.payment.domain.entity.MpTestAccount;
import com.acaboumony.payment.domain.enums.MpAccountType;
import com.acaboumony.payment.repository.MpTestAccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.Map;

@Service
public class MpOAuthService {

    private static final Logger log = LoggerFactory.getLogger(MpOAuthService.class);

    private final MpOAuthConfig config;
    private final MpTestAccountRepository repository;
    private final MpEncryptionService encryptionService;
    private final RestTemplate restTemplate;

    public MpOAuthService(MpOAuthConfig config,
                          MpTestAccountRepository repository,
                          MpEncryptionService encryptionService,
                          RestTemplate mpOAuthRestTemplate) {
        this.config = config;
        this.repository = repository;
        this.encryptionService = encryptionService;
        this.restTemplate = mpOAuthRestTemplate;
    }

    public String generateAuthorizationUrl() {
        var state = java.util.UUID.randomUUID().toString();
        return config.getAuthUrl()
            + "?client_id=" + config.getClientId()
            + "&redirect_uri=" + config.getRedirectUri()
            + "&response_type=code"
            + "&state=" + state;
    }

    public OAuthTokenResponse exchangeCode(String code) {
        var body = Map.of(
            "grant_type", "authorization_code",
            "client_id", config.getClientId(),
            "client_secret", config.getClientSecret(),
            "code", code,
            "redirect_uri", config.getRedirectUri()
        );
        try {
            var response = restTemplate.postForEntity(config.getTokenUrl(), body, Map.class);
            var tokenResponse = handleTokenResponse(response.getBody());
            persistTokens(tokenResponse);
            return tokenResponse;
        } catch (Exception e) {
            log.error("Failed to exchange authorization code", e);
            throw new RuntimeException("OAuth token exchange failed", e);
        }
    }

    public OAuthTokenResponse refreshAccessToken(String refreshToken) {
        var body = Map.of(
            "grant_type", "refresh_token",
            "client_id", config.getClientId(),
            "client_secret", config.getClientSecret(),
            "refresh_token", refreshToken
        );
        try {
            var response = restTemplate.postForEntity(config.getTokenUrl(), body, Map.class);
            var tokenResponse = handleTokenResponse(response.getBody());
            persistTokens(tokenResponse);
            return tokenResponse;
        } catch (Exception e) {
            log.error("Failed to refresh access token", e);
            throw new RuntimeException("OAuth token refresh failed", e);
        }
    }

    @SuppressWarnings("unchecked")
    private OAuthTokenResponse handleTokenResponse(Map<String, Object> body) {
        if (body == null) {
            throw new RuntimeException("Empty response from MP OAuth");
        }
        var accessToken = (String) body.get("access_token");
        var refreshToken = (String) body.get("refresh_token");
        var publicKey = (String) body.get("public_key");
        var expiresIn = body.get("expires_in");
        var expiresAt = expiresIn instanceof Number
            ? Instant.now().plusSeconds(((Number) expiresIn).longValue())
            : Instant.now().plusSeconds(21600);
        return new OAuthTokenResponse(accessToken, refreshToken, publicKey, expiresAt);
    }

    private void persistTokens(OAuthTokenResponse tokens) {
        var sellerOpt = repository.findByType(MpAccountType.SELLER);
        if (sellerOpt.isEmpty()) {
            log.warn("No seller account found to persist OAuth tokens");
            return;
        }
        var seller = sellerOpt.get();
        seller.setAccessTokenEnc(encryptionService.encrypt(tokens.accessToken()));
        if (tokens.refreshToken() != null) {
            seller.setRefreshTokenEnc(encryptionService.encrypt(tokens.refreshToken()));
        }
        seller.setPublicKey(tokens.publicKey());
        seller.setTokenExpiresAt(tokens.expiresAt());
        repository.save(seller);
        log.info("OAuth tokens persisted for seller account {}", seller.getEmail());
    }

    public record OAuthTokenResponse(
        String accessToken,
        String refreshToken,
        String publicKey,
        Instant expiresAt
    ) {}
}
