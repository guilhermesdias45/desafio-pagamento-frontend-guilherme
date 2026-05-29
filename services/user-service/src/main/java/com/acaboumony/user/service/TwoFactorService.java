package com.acaboumony.user.service;

import com.acaboumony.user.config.TotpProperties;
import com.acaboumony.user.domain.entity.RecoveryCode;
import com.acaboumony.user.domain.entity.User;
import com.acaboumony.user.dto.response.TwoFactorSetupResponse;
import com.acaboumony.user.event.UserEventProducer;
import com.acaboumony.user.exception.InvalidTotpCodeException;
import com.acaboumony.user.exception.RecoveryCodeExhaustedException;
import com.acaboumony.user.exception.RecoveryCodeInvalidException;
import com.acaboumony.user.exception.RefreshTokenInvalidException;
import com.acaboumony.user.exception.TwoFactorAlreadyEnabledException;
import com.acaboumony.user.exception.TwoFactorNotEnabledException;
import com.acaboumony.user.exception.UserNotFoundException;
import com.acaboumony.user.repository.RecoveryCodeRepository;
import com.acaboumony.user.repository.UserRepository;
import com.acaboumony.user.result.AuthResult;
import com.acaboumony.user.security.JwtTokenProvider;
import dev.samstevens.totp.code.CodeGenerator;
import dev.samstevens.totp.code.CodeVerifier;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.DefaultCodeVerifier;
import dev.samstevens.totp.code.HashingAlgorithm;
import dev.samstevens.totp.qr.QrData;
import dev.samstevens.totp.qr.QrGenerator;
import dev.samstevens.totp.qr.ZxingPngQrGenerator;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import dev.samstevens.totp.time.TimeProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * Manages the full 2FA lifecycle: setup, confirm, verify, disable, and recovery code login.
 */
@Service
public class TwoFactorService {

    private static final Logger log = LoggerFactory.getLogger(TwoFactorService.class);
    private static final int RECOVERY_CODE_COUNT = 8;

    private final TotpProperties totpProperties;
    private final AesGcmCryptoService cryptoService;
    private final UserRepository userRepository;
    private final RecoveryCodeRepository recoveryCodeRepository;
    private final UserEventProducer eventProducer;
    private final UserAuditLogger auditLogger;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenService refreshTokenService;
    private final StringRedisTemplate redis;
    private final BCryptPasswordEncoder bcrypt;

    // TOTP lib components (thread-safe, created once)
    private final CodeVerifier codeVerifier;
    private final QrGenerator qrGenerator;

    public TwoFactorService(TotpProperties totpProperties,
                            AesGcmCryptoService cryptoService,
                            UserRepository userRepository,
                            RecoveryCodeRepository recoveryCodeRepository,
                            UserEventProducer eventProducer,
                            UserAuditLogger auditLogger,
                            JwtTokenProvider jwtTokenProvider,
                            RefreshTokenService refreshTokenService,
                            StringRedisTemplate redis) {
        this.totpProperties = totpProperties;
        this.cryptoService = cryptoService;
        this.userRepository = userRepository;
        this.recoveryCodeRepository = recoveryCodeRepository;
        this.eventProducer = eventProducer;
        this.auditLogger = auditLogger;
        this.jwtTokenProvider = jwtTokenProvider;
        this.refreshTokenService = refreshTokenService;
        this.redis = redis;
        this.bcrypt = new BCryptPasswordEncoder(12);

        TimeProvider timeProvider = new SystemTimeProvider();
        CodeGenerator codeGenerator = new DefaultCodeGenerator(HashingAlgorithm.SHA1, 6);
        this.codeVerifier = new DefaultCodeVerifier(codeGenerator, timeProvider);
        this.qrGenerator = new ZxingPngQrGenerator();
    }

    /**
     * Initiates 2FA setup: generates secret + QR code + 8 recovery codes.
     * Secret is stored temporarily in Redis (TTL 10 min) — NOT persisted to DB yet.
     */
    public TwoFactorSetupResponse setup(UUID userId) {
        User user = userRepository.findById(userId).orElseThrow(UserNotFoundException::new);
        if (user.isTotpEnabled()) {
            throw new TwoFactorAlreadyEnabledException();
        }

        String secret = new DefaultSecretGenerator().generate();
        redis.opsForValue().set("2fa_setup:" + userId, secret, Duration.ofMinutes(10));

        String otpAuthUrl = new QrData.Builder()
                .label(user.getEmail())
                .secret(secret)
                .issuer(totpProperties.issuer())
                .algorithm(HashingAlgorithm.SHA1)
                .digits(6)
                .period(30)
                .build()
                .getUri();

        String qrCodeUrl;
        try {
            byte[] qrBytes = qrGenerator.generate(new QrData.Builder()
                    .label(user.getEmail()).secret(secret).issuer(totpProperties.issuer())
                    .algorithm(HashingAlgorithm.SHA1).digits(6).period(30).build());
            qrCodeUrl = "data:image/png;base64," + Base64.getEncoder().encodeToString(qrBytes);
        } catch (Exception e) {
            qrCodeUrl = otpAuthUrl; // fallback
        }

        List<String> recoveryCodes = generateRecoveryCodes();
        log.info("2FA setup initiated: userId={}", userId);
        return new TwoFactorSetupResponse(secret, qrCodeUrl, otpAuthUrl, recoveryCodes);
    }

    /**
     * Confirms 2FA setup: validates the first TOTP code, persists encrypted secret and recovery codes.
     */
    @Transactional
    public void confirm(UUID userId, String code) {
        String secret = redis.opsForValue().get("2fa_setup:" + userId);
        if (secret == null) {
            throw new InvalidTotpCodeException(); // setup expired
        }
        if (!codeVerifier.isValidCode(secret, code)) {
            throw new InvalidTotpCodeException();
        }

        User user = userRepository.findById(userId).orElseThrow(UserNotFoundException::new);

        user.setTotpSecretEncrypted(cryptoService.encrypt(secret));
        user.setTotpEnabled(true);
        userRepository.save(user);

        // Persist hashed recovery codes
        List<String> plaintextCodes = generateRecoveryCodes();
        for (String plainCode : plaintextCodes) {
            recoveryCodeRepository.save(RecoveryCode.builder()
                    .user(user)
                    .codeHash(bcrypt.encode(plainCode))
                    .build());
        }

        redis.delete("2fa_setup:" + userId);
        eventProducer.publishTwoFactorEnabled(userId);
        auditLogger.log(userId, "2FA_ENABLED", null, null);
        log.info("2FA confirmed: userId={}", userId);
    }

    /**
     * Verifies a TOTP code during login (called by AuthService).
     */
    public boolean verifyTotp(User user, String code) {
        if (!user.isTotpEnabled() || user.getTotpSecretEncrypted() == null) {
            return false;
        }
        String secret = cryptoService.decrypt(user.getTotpSecretEncrypted());
        return codeVerifier.isValidCode(secret, code);
    }

    /**
     * Completes the 2FA login gate: validates the twoFactorToken + code, issues tokens.
     */
    @Transactional
    public AuthResult verifyTwoFactorToken(String twoFactorToken, String code) {
        String userIdStr = redis.opsForValue().get("2fa_login:" + twoFactorToken);
        if (userIdStr == null) {
            return new AuthResult.Failure("INVALID_TOTP_CODE", "2FA session expired or invalid", false, null);
        }

        UUID userId = UUID.fromString(userIdStr);
        User user = userRepository.findById(userId).orElseThrow(UserNotFoundException::new);

        if (!verifyTotp(user, code)) {
            return new AuthResult.Failure("INVALID_TOTP_CODE", "Invalid 2FA code", false, null);
        }

        redis.delete("2fa_login:" + twoFactorToken);

        String accessToken = jwtTokenProvider.generateAccessToken(
                user.getId(), user.getEmail(), user.getRole(),
                user.getMerchant() != null ? user.getMerchant().getId() : null);
        String refreshToken = refreshTokenService.issue(user.getId());

        return new AuthResult.Success(accessToken, "Bearer", 900, false, refreshToken);
    }

    /**
     * Disables 2FA after verifying password and TOTP code.
     */
    @Transactional
    public void disable(UUID userId, String password, String code) {
        User user = userRepository.findById(userId).orElseThrow(UserNotFoundException::new);
        if (!user.isTotpEnabled()) {
            throw new TwoFactorNotEnabledException();
        }
        if (!bcrypt.matches(password, user.getPasswordHash())) {
            throw new InvalidTotpCodeException();
        }
        if (!verifyTotp(user, code)) {
            throw new InvalidTotpCodeException();
        }

        user.setTotpEnabled(false);
        user.setTotpSecretEncrypted(null);
        userRepository.save(user);
        recoveryCodeRepository.deleteByUserId(userId);
        auditLogger.log(userId, "2FA_DISABLED", null, null);
        log.info("2FA disabled: userId={}", userId);
    }

    /**
     * Uses a recovery code for emergency login after validating the twoFactorToken.
     */
    @Transactional
    public AuthResult useRecoveryCodeAndLogin(String twoFactorToken, String code) {
        String userIdStr = redis.opsForValue().get("2fa_login:" + twoFactorToken);
        if (userIdStr == null) {
            return new AuthResult.Failure("RECOVERY_CODE_INVALID", "2FA session expired or invalid", false, null);
        }

        UUID userId = UUID.fromString(userIdStr);
        List<RecoveryCode> unusedCodes = recoveryCodeRepository.findByUserIdAndUsedFalse(userId);

        if (unusedCodes.isEmpty()) {
            throw new RecoveryCodeExhaustedException();
        }

        RecoveryCode matched = unusedCodes.stream()
                .filter(rc -> bcrypt.matches(code, rc.getCodeHash()))
                .findFirst()
                .orElseThrow(RecoveryCodeInvalidException::new);

        matched.markUsed();
        recoveryCodeRepository.save(matched);

        redis.delete("2fa_login:" + twoFactorToken);
        auditLogger.log(userId, "2FA_RECOVERY_USED", null, null);

        User user = userRepository.findById(userId).orElseThrow(UserNotFoundException::new);
        String accessToken = jwtTokenProvider.generateAccessToken(
                user.getId(), user.getEmail(), user.getRole(),
                user.getMerchant() != null ? user.getMerchant().getId() : null);
        String refreshToken = refreshTokenService.issue(user.getId());

        return new AuthResult.Success(accessToken, "Bearer", 900, false, refreshToken);
    }

    // ─── private helpers ─────────────────────────────────────────────────────

    private List<String> generateRecoveryCodes() {
        List<String> codes = new ArrayList<>(RECOVERY_CODE_COUNT);
        for (int i = 0; i < RECOVERY_CODE_COUNT; i++) {
            codes.add(generateRecoveryCode());
        }
        return codes;
    }

    private String generateRecoveryCode() {
        // Format: XXXX-XXXX-XXXX-XXXX (4 groups of 4 alphanumeric chars)
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        java.util.Random rng = new java.security.SecureRandom();
        StringBuilder sb = new StringBuilder(19);
        for (int group = 0; group < 4; group++) {
            if (group > 0) sb.append('-');
            for (int i = 0; i < 4; i++) {
                sb.append(chars.charAt(rng.nextInt(chars.length())));
            }
        }
        return sb.toString();
    }
}
