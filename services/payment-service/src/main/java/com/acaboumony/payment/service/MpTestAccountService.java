package com.acaboumony.payment.service;

import com.acaboumony.payment.domain.entity.MpTestAccount;
import com.acaboumony.payment.domain.enums.MpAccountType;
import com.acaboumony.payment.repository.MpTestAccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

@Service
public class MpTestAccountService {

    private static final Logger log = LoggerFactory.getLogger(MpTestAccountService.class);

    private final MpTestAccountRepository repository;
    private final MpEncryptionService encryptionService;
    private final MpOAuthService oAuthService;

    public MpTestAccountService(MpTestAccountRepository repository,
                                MpEncryptionService encryptionService,
                                MpOAuthService oAuthService) {
        this.repository = repository;
        this.encryptionService = encryptionService;
        this.oAuthService = oAuthService;
    }

    public Optional<MpTestAccount> getSellerAccount() {
        return repository.findByType(MpAccountType.SELLER);
    }

    public Optional<String> getSellerAccessToken() {
        var sellerOpt = getSellerAccount();
        if (sellerOpt.isEmpty()) {
            log.debug("No seller account found, falling back to global token");
            return Optional.empty();
        }
        var seller = sellerOpt.get();
        if (seller.getAccessTokenEnc() == null) {
            log.debug("Seller access_token_enc is null, falling back to global token");
            return Optional.empty();
        }
        if (seller.getTokenExpiresAt() != null && Instant.now().isAfter(seller.getTokenExpiresAt())) {
            log.info("Seller access token expired, attempting refresh");
            try {
                var refreshTokenEnc = seller.getRefreshTokenEnc();
                if (refreshTokenEnc != null) {
                    var refreshToken = encryptionService.decrypt(refreshTokenEnc);
                    var refreshed = oAuthService.refreshAccessToken(refreshToken);
                    return Optional.of(refreshed.accessToken());
                }
            } catch (Exception e) {
                log.warn("Failed to refresh seller token, using expired token as fallback: {}", e.getMessage());
            }
        }
        return Optional.of(encryptionService.decrypt(seller.getAccessTokenEnc()));
    }
}
