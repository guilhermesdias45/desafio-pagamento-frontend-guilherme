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
import com.acaboumony.user.exception.EmailAlreadyExistsException;
import com.acaboumony.user.exception.EmailConfirmTokenInvalidException;
import com.acaboumony.user.exception.RefreshTokenInvalidException;
import com.acaboumony.user.repository.UserRepository;
import com.acaboumony.user.result.AuthResult;
import com.acaboumony.user.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceTest {

    @Mock UserRepository userRepository;
    @Mock MerchantService merchantService;
    @Mock UserEventProducer userEventProducer;
    @Mock LoginAttemptService loginAttemptService;
    @Mock JwtTokenProvider jwtTokenProvider;
    @Mock TwoFactorService twoFactorService;
    @Mock RefreshTokenService refreshTokenService;
    @Mock UserAuditLogger userAuditLogger;
    @Mock StringRedisTemplate stringRedisTemplate;
    @Mock ValueOperations<String, String> valueOps;

    AuthService authService;
    BCryptPasswordEncoder bcrypt;

    @BeforeEach
    void setUp() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
        authService = new AuthService(
                userRepository, merchantService, userEventProducer,
                loginAttemptService, jwtTokenProvider, twoFactorService,
                refreshTokenService, userAuditLogger, stringRedisTemplate);
        bcrypt = new BCryptPasswordEncoder(12);
    }

    // ─── register ─────────────────────────────────────────────────────────────

    @Test
    void deve_registrar_CUSTOMER_e_retornar_RegisterResponse() {
        var req = new RegisterRequest("ana@loja.com.br", "Senha@1234", "Ana Lima",
                UserRole.CUSTOMER, null, null);

        when(userRepository.existsByEmail("ana@loja.com.br")).thenReturn(false);
        User saved = userWithId(UserRole.CUSTOMER, UserStatus.PENDING_EMAIL_CONFIRMATION);
        when(userRepository.save(any())).thenReturn(saved);

        RegisterResponse resp = authService.register(req);

        assertThat(resp.email()).isEqualTo(saved.getEmail());
        assertThat(resp.role()).isEqualTo("CUSTOMER");
        assertThat(resp.merchantId()).isNull();
        verify(userEventProducer).publishUserRegistered(any(), any(), any(), any());
    }

    @Test
    void deve_registrar_MERCHANT_OWNER_e_criar_merchant() {
        var req = new RegisterRequest("dono@loja.com.br", "Senha@1234", "Dono",
                UserRole.MERCHANT_OWNER, "Loja S.A.", "12345678000195");

        when(userRepository.existsByEmail(any())).thenReturn(false);
        User saved = userWithId(UserRole.MERCHANT_OWNER, UserStatus.PENDING_EMAIL_CONFIRMATION);
        when(userRepository.save(any())).thenReturn(saved);

        Merchant merchant = merchantWithId(UUID.randomUUID());
        when(merchantService.createMerchant(any(), any(), any())).thenReturn(merchant);

        RegisterResponse resp = authService.register(req);

        assertThat(resp.merchantId()).isEqualTo(merchant.getId());
        verify(merchantService).createMerchant(any(), eq("Loja S.A."), eq("12345678000195"));
    }

    @Test
    void deve_lancar_EmailAlreadyExistsException_quando_email_ja_existe() {
        var req = new RegisterRequest("dup@loja.com.br", "Senha@1234", "Ana",
                UserRole.CUSTOMER, null, null);
        when(userRepository.existsByEmail("dup@loja.com.br")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(req))
                .isInstanceOf(EmailAlreadyExistsException.class);

        verify(userRepository, never()).save(any());
    }

    // ─── authenticate ─────────────────────────────────────────────────────────

    @Test
    void deve_retornar_AuthResult_Success_quando_credenciais_corretas() {
        String rawPassword = "Senha@1234";
        User user = userWithPassword(rawPassword, UserStatus.ACTIVE, UserRole.CUSTOMER);

        when(loginAttemptService.isLocked(any())).thenReturn(false);
        when(userRepository.findByEmail("ana@loja.com.br")).thenReturn(Optional.of(user));
        when(jwtTokenProvider.generateAccessToken(any(), any(), any(), any())).thenReturn("access.jwt");
        when(refreshTokenService.issue(any())).thenReturn("refresh-token");

        var req = new LoginRequest("ana@loja.com.br", rawPassword, null, null);
        AuthResult result = authService.authenticate(req);

        assertThat(result).isInstanceOf(AuthResult.Success.class);
        var success = (AuthResult.Success) result;
        assertThat(success.accessToken()).isEqualTo("access.jwt");
        assertThat(success.refreshToken()).isEqualTo("refresh-token");
    }

    @Test
    void deve_retornar_Failure_ACCOUNT_LOCKED_quando_conta_bloqueada() {
        when(loginAttemptService.isLocked("ana@loja.com.br")).thenReturn(true);
        when(loginAttemptService.getUnlockAt("ana@loja.com.br"))
                .thenReturn(Optional.of(Instant.now().plusSeconds(1800)));

        var req = new LoginRequest("ana@loja.com.br", "qualquer", null, null);
        AuthResult result = authService.authenticate(req);

        assertThat(result).isInstanceOf(AuthResult.Failure.class);
        assertThat(((AuthResult.Failure) result).errorCode()).isEqualTo("ACCOUNT_LOCKED");
    }

    @Test
    void deve_retornar_Failure_INVALID_CREDENTIALS_quando_senha_errada() {
        User user = userWithPassword("Senha@1234", UserStatus.ACTIVE, UserRole.CUSTOMER);
        when(loginAttemptService.isLocked(any())).thenReturn(false);
        when(userRepository.findByEmail(any())).thenReturn(Optional.of(user));
        when(loginAttemptService.recordFailure(any()))
                .thenReturn(new LoginAttemptService.LoginAttemptResult(1, false, null));

        var req = new LoginRequest("ana@loja.com.br", "senha-errada", null, null);
        AuthResult result = authService.authenticate(req);

        assertThat(((AuthResult.Failure) result).errorCode()).isEqualTo("INVALID_CREDENTIALS");
    }

    @Test
    void deve_retornar_Failure_INVALID_CREDENTIALS_quando_email_nao_existe() {
        when(loginAttemptService.isLocked(any())).thenReturn(false);
        when(userRepository.findByEmail(any())).thenReturn(Optional.empty());
        when(loginAttemptService.recordFailure(any()))
                .thenReturn(new LoginAttemptService.LoginAttemptResult(1, false, null));

        var req = new LoginRequest("naoexiste@loja.com.br", "qualquer", null, null);
        AuthResult result = authService.authenticate(req);

        assertThat(((AuthResult.Failure) result).errorCode()).isEqualTo("INVALID_CREDENTIALS");
    }

    @Test
    void deve_publicar_LoginBlocked_quando_atinge_limite_e_bloqueia() {
        User user = userWithPassword("Senha@1234", UserStatus.ACTIVE, UserRole.CUSTOMER);
        when(loginAttemptService.isLocked(any())).thenReturn(false);
        when(userRepository.findByEmail(any())).thenReturn(Optional.of(user));
        Instant unlockAt = Instant.now().plusSeconds(1800);
        when(loginAttemptService.recordFailure(any()))
                .thenReturn(new LoginAttemptService.LoginAttemptResult(5, true, unlockAt));

        authService.authenticate(new LoginRequest("ana@loja.com.br", "errada", null, null));

        verify(userEventProducer).publishLoginBlocked(any(), any(), any());
    }

    @Test
    void deve_retornar_Failure_ACCOUNT_NOT_CONFIRMED_quando_status_PENDING() {
        User user = userWithPassword("Senha@1234", UserStatus.PENDING_EMAIL_CONFIRMATION, UserRole.CUSTOMER);
        when(loginAttemptService.isLocked(any())).thenReturn(false);
        when(userRepository.findByEmail(any())).thenReturn(Optional.of(user));

        AuthResult result = authService.authenticate(new LoginRequest("ana@loja.com.br", "Senha@1234", null, null));

        assertThat(((AuthResult.Failure) result).errorCode()).isEqualTo("ACCOUNT_NOT_CONFIRMED");
    }

    @Test
    void deve_retornar_Failure_ACCOUNT_DISABLED_quando_status_DISABLED() {
        User user = userWithPassword("Senha@1234", UserStatus.DISABLED, UserRole.CUSTOMER);
        when(loginAttemptService.isLocked(any())).thenReturn(false);
        when(userRepository.findByEmail(any())).thenReturn(Optional.of(user));

        AuthResult result = authService.authenticate(new LoginRequest("ana@loja.com.br", "Senha@1234", null, null));

        assertThat(((AuthResult.Failure) result).errorCode()).isEqualTo("ACCOUNT_DISABLED");
    }

    @Test
    void deve_retornar_RequiresTwoFactor_quando_2FA_ativo_e_sem_codigo() {
        User user = userWithPasswordAnd2FA("Senha@1234", true);
        when(loginAttemptService.isLocked(any())).thenReturn(false);
        when(userRepository.findByEmail(any())).thenReturn(Optional.of(user));

        AuthResult result = authService.authenticate(new LoginRequest("ana@loja.com.br", "Senha@1234", null, null));

        assertThat(result).isInstanceOf(AuthResult.RequiresTwoFactor.class);
        verify(valueOps).set(anyString(), anyString(), any());
    }

    @Test
    void deve_retornar_Failure_INVALID_TOTP_CODE_quando_2FA_ativo_e_codigo_errado() {
        User user = userWithPasswordAnd2FA("Senha@1234", true);
        when(loginAttemptService.isLocked(any())).thenReturn(false);
        when(userRepository.findByEmail(any())).thenReturn(Optional.of(user));
        when(twoFactorService.verifyTotp(any(), eq("000000"))).thenReturn(false);

        AuthResult result = authService.authenticate(
                new LoginRequest("ana@loja.com.br", "Senha@1234", "000000", null));

        assertThat(((AuthResult.Failure) result).errorCode()).isEqualTo("INVALID_TOTP_CODE");
    }

    @Test
    void deve_autenticar_com_sucesso_quando_2FA_ativo_e_codigo_correto() {
        User user = userWithPasswordAnd2FA("Senha@1234", true);
        when(loginAttemptService.isLocked(any())).thenReturn(false);
        when(userRepository.findByEmail(any())).thenReturn(Optional.of(user));
        when(twoFactorService.verifyTotp(any(), eq("123456"))).thenReturn(true);
        when(jwtTokenProvider.generateAccessToken(any(), any(), any(), any())).thenReturn("access.jwt");
        when(refreshTokenService.issue(any())).thenReturn("refresh-token");

        AuthResult result = authService.authenticate(
                new LoginRequest("ana@loja.com.br", "Senha@1234", "123456", null));

        assertThat(result).isInstanceOf(AuthResult.Success.class);
    }

    // ─── refresh ──────────────────────────────────────────────────────────────

    @Test
    void deve_rotacionar_refresh_token_e_retornar_novos_tokens() {
        UUID userId = UUID.randomUUID();
        User user = userWithId(UserRole.CUSTOMER, UserStatus.ACTIVE);

        when(refreshTokenService.validateAndDelete("old-token")).thenReturn(Optional.of(userId));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(jwtTokenProvider.generateAccessToken(any(), any(), any(), any())).thenReturn("new.access.jwt");
        when(refreshTokenService.issue(any())).thenReturn("new-refresh");

        RefreshResponse resp = authService.refresh("old-token");

        assertThat(resp.accessToken()).isEqualTo("new.access.jwt");
        assertThat(resp.refreshToken()).isEqualTo("new-refresh");
    }

    @Test
    void deve_lancar_RefreshTokenInvalidException_quando_token_invalido() {
        when(refreshTokenService.validateAndDelete("bad-token")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh("bad-token"))
                .isInstanceOf(RefreshTokenInvalidException.class);
    }

    @Test
    void deve_lancar_RefreshTokenInvalidException_quando_usuario_nao_encontrado_no_refresh() {
        UUID userId = UUID.randomUUID();
        when(refreshTokenService.validateAndDelete(any())).thenReturn(Optional.of(userId));
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh("valid-token"))
                .isInstanceOf(RefreshTokenInvalidException.class);
    }

    // ─── logout ───────────────────────────────────────────────────────────────

    @Test
    void deve_revogar_refresh_token_no_logout() {
        UUID userId = UUID.randomUUID();
        authService.logout(userId, "some-token");

        verify(refreshTokenService).revoke("some-token");
        verify(userAuditLogger).log(eq(userId), eq("LOGOUT"), any(), any());
    }

    // ─── confirmEmail ─────────────────────────────────────────────────────────

    @Test
    void deve_confirmar_email_e_ativar_usuario() {
        UUID userId = UUID.randomUUID();
        User user = userWithId(UserRole.CUSTOMER, UserStatus.PENDING_EMAIL_CONFIRMATION);

        when(valueOps.get("email_confirm:valid-token")).thenReturn(userId.toString());
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenReturn(user);

        authService.confirmEmail("valid-token");

        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        verify(stringRedisTemplate).delete("email_confirm:valid-token");
    }

    @Test
    void deve_lancar_EmailConfirmTokenInvalidException_quando_token_expirado() {
        when(valueOps.get("email_confirm:expired")).thenReturn(null);

        assertThatThrownBy(() -> authService.confirmEmail("expired"))
                .isInstanceOf(EmailConfirmTokenInvalidException.class);
    }

    @Test
    void deve_lancar_EmailConfirmTokenInvalidException_quando_usuario_nao_encontrado() {
        UUID userId = UUID.randomUUID();
        when(valueOps.get(anyString())).thenReturn(userId.toString());
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.confirmEmail("valid-token"))
                .isInstanceOf(EmailConfirmTokenInvalidException.class);
    }

    // ─── helpers ──────────────────────────────────────────────────────────────

    private User userWithId(UserRole role, UserStatus status) {
        User user = User.builder()
                .email("ana@loja.com.br")
                .passwordHash(bcrypt.encode("Senha@1234"))
                .fullName("Ana Lima")
                .role(role)
                .status(status)
                .build();
        initEntity(user);
        return user;
    }

    private User userWithPassword(String rawPassword, UserStatus status, UserRole role) {
        User user = User.builder()
                .email("ana@loja.com.br")
                .passwordHash(bcrypt.encode(rawPassword))
                .fullName("Ana Lima")
                .role(role)
                .status(status)
                .build();
        initEntity(user);
        return user;
    }

    private User userWithPasswordAnd2FA(String rawPassword, boolean totpEnabled) {
        User user = User.builder()
                .email("ana@loja.com.br")
                .passwordHash(bcrypt.encode(rawPassword))
                .fullName("Ana Lima")
                .role(UserRole.CUSTOMER)
                .status(UserStatus.ACTIVE)
                .build();
        initEntity(user);
        user.setTotpEnabled(totpEnabled);
        user.setTotpSecretEncrypted("encrypted-secret");
        return user;
    }

    private Merchant merchantWithId(UUID id) {
        Merchant m = Merchant.builder()
                .companyName("Loja S.A.")
                .cnpj("12345678000195")
                .owner(userWithId(UserRole.MERCHANT_OWNER, UserStatus.ACTIVE))
                .build();
        initEntity(m);
        return m;
    }

    private static void initEntity(Object entity) {
        ReflectionTestUtils.invokeMethod(entity, "prePersist");
    }
}
