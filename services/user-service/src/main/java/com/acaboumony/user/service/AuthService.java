package com.acaboumony.user.service;

import com.acaboumony.user.domain.entity.Merchant;
import com.acaboumony.user.domain.entity.User;
import com.acaboumony.user.domain.enums.UserRole;
import com.acaboumony.user.domain.enums.UserStatus;
import com.acaboumony.user.dto.request.LoginRequest;
import com.acaboumony.user.dto.request.RegisterRequest;
import com.acaboumony.user.dto.response.RefreshResponse;
import com.acaboumony.user.dto.response.RegisterResponse;
import com.acaboumony.user.event.UserEventProducer;
import com.acaboumony.user.exception.AccountLockedException;
import com.acaboumony.user.exception.AccountNotConfirmedException;
import com.acaboumony.user.exception.EmailAlreadyExistsException;
import com.acaboumony.user.exception.EmailConfirmTokenInvalidException;
import com.acaboumony.user.exception.InvalidCredentialsException;
import com.acaboumony.user.exception.RefreshTokenInvalidException;
import com.acaboumony.user.repository.UserRepository;
import com.acaboumony.user.result.AuthResult;
import com.acaboumony.user.security.JwtTokenProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Core authentication service: register, authenticate, refresh, logout, confirm-email.
 *
 * <p>Methods are added incrementally across tasks 14, 15, 17, 18, and 20.</p>
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    /**
     * Dummy BCrypt hash used for constant-time password comparison when the email does not exist.
     * This prevents timing attacks that could enumerate valid email addresses.
     */
    static final String DUMMY_BCRYPT_HASH =
            "$2a$12$DummyHashForTimingAttackPrevention.XXXXXXXXXXXXXXXXXXXXXXX";

    private final UserRepository userRepository;
    private final MerchantService merchantService;
    private final UserEventProducer userEventProducer;
    private final LoginAttemptService loginAttemptService;
    private final JwtTokenProvider jwtTokenProvider;
    private final TwoFactorService twoFactorService;
    private final RefreshTokenService refreshTokenService;
    private final UserAuditLogger userAuditLogger;
    private final StringRedisTemplate stringRedisTemplate;
    private final BCryptPasswordEncoder bcrypt;

    public AuthService(UserRepository userRepository,
                       MerchantService merchantService,
                       UserEventProducer userEventProducer,
                       LoginAttemptService loginAttemptService,
                       JwtTokenProvider jwtTokenProvider,
                       TwoFactorService twoFactorService,
                       RefreshTokenService refreshTokenService,
                       UserAuditLogger userAuditLogger,
                       StringRedisTemplate stringRedisTemplate) {
        this.userRepository = userRepository;
        this.merchantService = merchantService;
        this.userEventProducer = userEventProducer;
        this.loginAttemptService = loginAttemptService;
        this.jwtTokenProvider = jwtTokenProvider;
        this.twoFactorService = twoFactorService;
        this.refreshTokenService = refreshTokenService;
        this.userAuditLogger = userAuditLogger;
        this.stringRedisTemplate = stringRedisTemplate;
        this.bcrypt = new BCryptPasswordEncoder(12);
    }

    // ─── Task 14: register ────────────────────────────────────────────────────

    /**
     * Registers a new user (CUSTOMER or MERCHANT_OWNER).
     *
     * <p>MERCHANT_OWNER creates both a {@link User} and a {@link Merchant} atomically.
     * A confirmation token is stored in Redis (TTL 24 h) for the email confirmation flow.</p>
     *
     * @param req validated registration request
     * @return registration response with userId, email, role, merchantId (null for CUSTOMER)
     * @throws EmailAlreadyExistsException if email is already taken
     * @throws com.acaboumony.user.exception.CnpjAlreadyRegisteredException if CNPJ is already registered
     */
    @Transactional
    public RegisterResponse register(RegisterRequest req) {
        if (userRepository.existsByEmail(req.email())) {
            throw new EmailAlreadyExistsException();
        }

        User user = User.builder()
                .email(req.email())
                .passwordHash(bcrypt.encode(req.password()))
                .fullName(req.fullName())
                .role(req.role())
                .status(UserStatus.PENDING_EMAIL_CONFIRMATION)
                .build();
        user = userRepository.save(user);

        UUID merchantId = null;
        if (req.role() == UserRole.MERCHANT_OWNER) {
            Merchant merchant = merchantService.createMerchant(user, req.companyName(), req.cnpj());
            user.setMerchant(merchant);
            userRepository.save(user);
            merchantId = merchant.getId();
        }

        // Store email confirmation token in Redis (TTL 24h)
        String confirmToken = UUID.randomUUID().toString();
        stringRedisTemplate.opsForValue().set(
                "email_confirm:" + confirmToken,
                user.getId().toString(),
                Duration.ofHours(24));

        userEventProducer.publishUserRegistered(user.getId(), user.getEmail(), user.getRole(), merchantId);
        log.info("User registered: userId={}, role={}", user.getId(), user.getRole());

        return new RegisterResponse(user.getId(), user.getEmail(), user.getRole().name(), merchantId, false);
    }

    // ─── Task 15: authenticate ────────────────────────────────────────────────

    /**
     * Authenticates a user by email + password, applying rate-limiting and 2FA gate.
     */
    @Transactional
    public AuthResult authenticate(LoginRequest req) {
        // 1. Rate limit / locked check
        if (loginAttemptService.isLocked(req.email())) {
            Instant unlockAt = loginAttemptService.getUnlockAt(req.email()).orElse(null);
            return new AuthResult.Failure("ACCOUNT_LOCKED", "Account temporarily locked", false, unlockAt);
        }

        // 2. Fetch user (may be null — we still run BCrypt for constant-time)
        User user = userRepository.findByEmail(req.email()).orElse(null);

        // 3. Constant-time BCrypt comparison — always executes regardless of user existence
        String hash = user != null ? user.getPasswordHash() : DUMMY_BCRYPT_HASH;
        boolean passwordOk = bcrypt.matches(req.password(), hash);

        if (user == null || !passwordOk) {
            LoginAttemptService.LoginAttemptResult r = loginAttemptService.recordFailure(req.email());
            if (r.nowLocked()) {
                userEventProducer.publishLoginBlocked(
                        user != null ? user.getId() : null,
                        req.email(),
                        r.unlockAt());
            }
            return new AuthResult.Failure("INVALID_CREDENTIALS", "Invalid email or password", false, null);
        }

        // 4. Account status checks
        if (user.getStatus() == UserStatus.PENDING_EMAIL_CONFIRMATION) {
            return new AuthResult.Failure("ACCOUNT_NOT_CONFIRMED", "Email not confirmed", false, null);
        }
        if (user.getStatus() == UserStatus.DISABLED) {
            return new AuthResult.Failure("ACCOUNT_DISABLED", "Account has been disabled", false, null);
        }

        // 5. 2FA gate
        if (user.isTotpEnabled() && (req.totpCode() == null || req.totpCode().isBlank())) {
            String twoFactorToken = UUID.randomUUID().toString();
            stringRedisTemplate.opsForValue().set(
                    "2fa_login:" + twoFactorToken,
                    user.getId().toString(),
                    Duration.ofMinutes(5));
            return new AuthResult.RequiresTwoFactor(true, twoFactorToken);
        }
        if (user.isTotpEnabled()) {
            if (!twoFactorService.verifyTotp(user, req.totpCode())) {
                return new AuthResult.Failure("INVALID_TOTP_CODE", "Invalid 2FA code", false, null);
            }
        }

        // 6. Success: generate tokens
        String accessToken = jwtTokenProvider.generateAccessToken(
                user.getId(), user.getEmail(), user.getRole(),
                user.getMerchant() != null ? user.getMerchant().getId() : null);
        String refreshToken = refreshTokenService.issue(user.getId());

        loginAttemptService.recordSuccess(req.email());
        userEventProducer.publishLoginSuccess(user.getId(), user.getEmail(), req.deviceFingerprint());
        userAuditLogger.log(user.getId(), "LOGIN_SUCCESS", null, req.deviceFingerprint());

        log.info("Login successful: userId={}", user.getId());
        return new AuthResult.Success(accessToken, "Bearer", 900, false, refreshToken);
    }

    // ─── Task 17: refresh ─────────────────────────────────────────────────────

    /**
     * Rotates the refresh token: validates and deletes the old one, issues a new one.
     */
    @Transactional
    public RefreshResponse refresh(String oldRefreshToken) {
        UUID userId = refreshTokenService.validateAndDelete(oldRefreshToken)
                .orElseThrow(RefreshTokenInvalidException::new);

        User user = userRepository.findById(userId)
                .orElseThrow(RefreshTokenInvalidException::new);

        String newAccessToken = jwtTokenProvider.generateAccessToken(
                user.getId(), user.getEmail(), user.getRole(),
                user.getMerchant() != null ? user.getMerchant().getId() : null);
        String newRefreshToken = refreshTokenService.issue(user.getId());

        userAuditLogger.log(user.getId(), "REFRESH_SUCCESS", null, null);
        log.info("Refresh token rotated: userId={}", userId);

        return new RefreshResponse(newAccessToken, "Bearer", 900, newRefreshToken);
    }

    // ─── Task 18: logout ──────────────────────────────────────────────────────

    /**
     * Logs out a specific session by revoking its refresh token.
     * Idempotent — calling with an already-revoked token is a no-op.
     */
    public void logout(UUID userId, String refreshToken) {
        refreshTokenService.revoke(refreshToken);
        userAuditLogger.log(userId, "LOGOUT", null, null);
        log.info("Logout: userId={}", userId);
    }

    // ─── Task 20: confirmEmail ────────────────────────────────────────────────

    /**
     * Confirms a user's email address using the token from the confirmation email.
     */
    @Transactional
    public void confirmEmail(String token) {
        String key = "email_confirm:" + token;
        String userIdStr = stringRedisTemplate.opsForValue().get(key);
        if (userIdStr == null) {
            throw new EmailConfirmTokenInvalidException();
        }
        UUID userId = UUID.fromString(userIdStr);
        User user = userRepository.findById(userId)
                .orElseThrow(EmailConfirmTokenInvalidException::new);
        user.setStatus(UserStatus.ACTIVE);
        userRepository.save(user);
        stringRedisTemplate.delete(key);
        log.info("Email confirmed: userId={}", userId);
    }
}
