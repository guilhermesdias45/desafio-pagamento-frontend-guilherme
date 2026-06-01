package com.acaboumony.user.service;

import com.acaboumony.user.config.TotpProperties;
import com.acaboumony.user.domain.entity.RecoveryCode;
import com.acaboumony.user.domain.entity.User;
import com.acaboumony.user.domain.enums.UserRole;
import com.acaboumony.user.domain.enums.UserStatus;
import com.acaboumony.user.dto.response.TwoFactorSetupResponse;
import com.acaboumony.user.event.UserEventProducer;
import com.acaboumony.user.exception.InvalidTotpCodeException;
import com.acaboumony.user.exception.RecoveryCodeExhaustedException;
import com.acaboumony.user.exception.RecoveryCodeInvalidException;
import com.acaboumony.user.exception.TwoFactorAlreadyEnabledException;
import com.acaboumony.user.exception.TwoFactorNotEnabledException;
import com.acaboumony.user.exception.UserNotFoundException;
import com.acaboumony.user.repository.RecoveryCodeRepository;
import com.acaboumony.user.repository.UserRepository;
import com.acaboumony.user.result.AuthResult;
import com.acaboumony.user.security.JwtTokenProvider;
import dev.samstevens.totp.code.CodeVerifier;
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

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TwoFactorServiceTest {

    @Mock TotpProperties totpProperties;
    @Mock AesGcmCryptoService cryptoService;
    @Mock UserRepository userRepository;
    @Mock RecoveryCodeRepository recoveryCodeRepository;
    @Mock UserEventProducer eventProducer;
    @Mock UserAuditLogger auditLogger;
    @Mock JwtTokenProvider jwtTokenProvider;
    @Mock RefreshTokenService refreshTokenService;
    @Mock StringRedisTemplate redis;
    @Mock ValueOperations<String, String> valueOps;
    @Mock CodeVerifier codeVerifier;

    TwoFactorService twoFactorService;
    BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder(12);

    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(totpProperties.issuer()).thenReturn("AcabouoMony");
        when(totpProperties.aesKey()).thenReturn("0".repeat(64)); // 32 bytes hex
        twoFactorService = new TwoFactorService(
                totpProperties, cryptoService, userRepository, recoveryCodeRepository,
                eventProducer, auditLogger, jwtTokenProvider, refreshTokenService, redis);
        // Inject mock CodeVerifier to control TOTP code validation in unit tests
        ReflectionTestUtils.setField(twoFactorService, "codeVerifier", codeVerifier);
    }

    // ─── setup ────────────────────────────────────────────────────────────────

    @Test
    void deve_retornar_TwoFactorSetupResponse_com_secret_e_8_recovery_codes() {
        UUID userId = UUID.randomUUID();
        User user = buildUser(userId, false);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        TwoFactorSetupResponse resp = twoFactorService.setup(userId);

        assertThat(resp.secret()).isNotBlank();
        assertThat(resp.recoveryCodes()).hasSize(8);
        assertThat(resp.qrCodeUrl()).isNotBlank();
        verify(valueOps).set(anyString(), anyString(), any());
    }

    @Test
    void deve_lancar_UserNotFoundException_no_setup_quando_usuario_nao_existe() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> twoFactorService.setup(userId))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void deve_lancar_TwoFactorAlreadyEnabledException_quando_2FA_ja_ativo() {
        UUID userId = UUID.randomUUID();
        User user = buildUser(userId, true);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> twoFactorService.setup(userId))
                .isInstanceOf(TwoFactorAlreadyEnabledException.class);
    }

    // ─── confirm ──────────────────────────────────────────────────────────────

    @Test
    void deve_lancar_InvalidTotpCodeException_quando_setup_expirado() {
        UUID userId = UUID.randomUUID();
        when(valueOps.get("2fa_setup:" + userId)).thenReturn(null);

        assertThatThrownBy(() -> twoFactorService.confirm(userId, "123456"))
                .isInstanceOf(InvalidTotpCodeException.class);
    }

    @Test
    void deve_confirmar_2FA_com_sucesso_e_persistir_secret_e_recovery_codes() {
        UUID userId = UUID.randomUUID();
        User user = buildUser(userId, false);

        when(valueOps.get("2fa_setup:" + userId)).thenReturn("JBSWY3DPEHPK3PXP");
        when(codeVerifier.isValidCode("JBSWY3DPEHPK3PXP", "123456")).thenReturn(true);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(cryptoService.encrypt("JBSWY3DPEHPK3PXP")).thenReturn("encrypted-secret");
        when(userRepository.save(any())).thenReturn(user);
        when(recoveryCodeRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        twoFactorService.confirm(userId, "123456");

        assertThat(user.isTotpEnabled()).isTrue();
        assertThat(user.getTotpSecretEncrypted()).isEqualTo("encrypted-secret");
        verify(recoveryCodeRepository, org.mockito.Mockito.times(8)).save(any());
        verify(redis).delete("2fa_setup:" + userId);
        verify(eventProducer).publishTwoFactorEnabled(userId);
    }

    @Test
    void deve_lancar_InvalidTotpCodeException_quando_codigo_invalido_no_confirm() {
        UUID userId = UUID.randomUUID();
        when(valueOps.get("2fa_setup:" + userId)).thenReturn("JBSWY3DPEHPK3PXP");
        when(codeVerifier.isValidCode("JBSWY3DPEHPK3PXP", "000000")).thenReturn(false);

        assertThatThrownBy(() -> twoFactorService.confirm(userId, "000000"))
                .isInstanceOf(InvalidTotpCodeException.class);
    }

    @Test
    void deve_lancar_UserNotFoundException_no_confirm_quando_usuario_nao_existe() {
        UUID userId = UUID.randomUUID();
        when(valueOps.get("2fa_setup:" + userId)).thenReturn("JBSWY3DPEHPK3PXP");
        when(codeVerifier.isValidCode(any(), any())).thenReturn(true);
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> twoFactorService.confirm(userId, "123456"))
                .isInstanceOf(UserNotFoundException.class);
    }

    // ─── verifyTotp ───────────────────────────────────────────────────────────

    @Test
    void deve_retornar_false_quando_2FA_nao_ativo() {
        User user = buildUser(UUID.randomUUID(), false);
        assertThat(twoFactorService.verifyTotp(user, "123456")).isFalse();
    }

    @Test
    void deve_retornar_false_quando_secret_nulo() {
        UUID userId = UUID.randomUUID();
        User user = buildUser(userId, true);
        user.setTotpSecretEncrypted(null);

        assertThat(twoFactorService.verifyTotp(user, "123456")).isFalse();
    }

    @Test
    void deve_descriptografar_secret_e_verificar_codigo_via_cryptoService() {
        UUID userId = UUID.randomUUID();
        User user = buildUser(userId, true);
        user.setTotpSecretEncrypted("encrypted-secret");
        when(cryptoService.decrypt("encrypted-secret")).thenReturn("JBSWY3DPEHPK3PXP");

        // Any TOTP code will be invalid (we don't know the real-time code)
        // but the key flow — decrypt + verify — is exercised
        boolean result = twoFactorService.verifyTotp(user, "000000");
        assertThat(result).isFalse(); // 000000 is virtually never correct
        verify(cryptoService).decrypt("encrypted-secret");
    }

    // ─── verifyTwoFactorToken ─────────────────────────────────────────────────

    @Test
    void deve_retornar_Failure_quando_2fa_login_token_expirado() {
        when(valueOps.get("2fa_login:expired-token")).thenReturn(null);

        AuthResult result = twoFactorService.verifyTwoFactorToken("expired-token", "123456");

        assertThat(result).isInstanceOf(AuthResult.Failure.class);
        assertThat(((AuthResult.Failure) result).errorCode()).isEqualTo("INVALID_TOTP_CODE");
    }

    @Test
    void deve_retornar_Failure_quando_codigo_totp_invalido_no_verify() {
        UUID userId = UUID.randomUUID();
        User user = buildUser(userId, true);
        user.setTotpSecretEncrypted("enc");

        when(valueOps.get("2fa_login:valid-token")).thenReturn(userId.toString());
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(cryptoService.decrypt("enc")).thenReturn("JBSWY3DPEHPK3PXP");
        when(codeVerifier.isValidCode("JBSWY3DPEHPK3PXP", "000000")).thenReturn(false);

        AuthResult result = twoFactorService.verifyTwoFactorToken("valid-token", "000000");

        assertThat(((AuthResult.Failure) result).errorCode()).isEqualTo("INVALID_TOTP_CODE");
    }

    @Test
    void deve_retornar_Success_quando_codigo_totp_valido_no_verifyTwoFactorToken() {
        UUID userId = UUID.randomUUID();
        User user = buildUser(userId, true);
        user.setTotpSecretEncrypted("enc");

        when(valueOps.get("2fa_login:token")).thenReturn(userId.toString());
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(cryptoService.decrypt("enc")).thenReturn("JBSWY3DPEHPK3PXP");
        when(codeVerifier.isValidCode("JBSWY3DPEHPK3PXP", "123456")).thenReturn(true);
        when(jwtTokenProvider.generateAccessToken(any(), any(), any(), any())).thenReturn("access.jwt");
        when(refreshTokenService.issue(any())).thenReturn("refresh-token");

        AuthResult result = twoFactorService.verifyTwoFactorToken("token", "123456");

        assertThat(result).isInstanceOf(AuthResult.Success.class);
        verify(redis).delete("2fa_login:token");
    }

    // ─── disable ──────────────────────────────────────────────────────────────

    @Test
    void deve_lancar_UserNotFoundException_no_disable_quando_usuario_nao_existe() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> twoFactorService.disable(userId, "pass", "code"))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void deve_lancar_TwoFactorNotEnabledException_quando_2FA_nao_ativo() {
        UUID userId = UUID.randomUUID();
        User user = buildUser(userId, false);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> twoFactorService.disable(userId, "pass", "code"))
                .isInstanceOf(TwoFactorNotEnabledException.class);
    }

    @Test
    void deve_lancar_InvalidTotpCodeException_no_disable_quando_senha_errada() {
        UUID userId = UUID.randomUUID();
        User user = buildUser(userId, true);
        user.setTotpSecretEncrypted("enc");
        user.setPasswordHash(bcrypt.encode("Senha@1234"));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> twoFactorService.disable(userId, "errada", "code"))
                .isInstanceOf(InvalidTotpCodeException.class);
    }

    @Test
    void deve_lancar_InvalidTotpCodeException_no_disable_quando_totp_invalido() {
        UUID userId = UUID.randomUUID();
        User user = buildUser(userId, true);
        user.setTotpSecretEncrypted("enc");
        user.setPasswordHash(bcrypt.encode("Senha@1234"));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(cryptoService.decrypt("enc")).thenReturn("JBSWY3DPEHPK3PXP");
        when(codeVerifier.isValidCode("JBSWY3DPEHPK3PXP", "000000")).thenReturn(false);

        assertThatThrownBy(() -> twoFactorService.disable(userId, "Senha@1234", "000000"))
                .isInstanceOf(InvalidTotpCodeException.class);
    }

    @Test
    void deve_desativar_2FA_com_sucesso() {
        UUID userId = UUID.randomUUID();
        User user = buildUser(userId, true);
        user.setTotpSecretEncrypted("enc");
        user.setPasswordHash(bcrypt.encode("Senha@1234"));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(cryptoService.decrypt("enc")).thenReturn("JBSWY3DPEHPK3PXP");
        when(codeVerifier.isValidCode("JBSWY3DPEHPK3PXP", "123456")).thenReturn(true);
        when(userRepository.save(any())).thenReturn(user);

        twoFactorService.disable(userId, "Senha@1234", "123456");

        assertThat(user.isTotpEnabled()).isFalse();
        assertThat(user.getTotpSecretEncrypted()).isNull();
        verify(recoveryCodeRepository).deleteByUserId(userId);
    }

    // ─── useRecoveryCodeAndLogin ──────────────────────────────────────────────

    @Test
    void deve_retornar_Failure_quando_2fa_login_token_expirado_no_recovery() {
        when(valueOps.get("2fa_login:bad-token")).thenReturn(null);

        AuthResult result = twoFactorService.useRecoveryCodeAndLogin("bad-token", "AAAA-BBBB-CCCC-DDDD");

        assertThat(((AuthResult.Failure) result).errorCode()).isEqualTo("RECOVERY_CODE_INVALID");
    }

    @Test
    void deve_lancar_RecoveryCodeExhaustedException_quando_todos_codigos_usados() {
        UUID userId = UUID.randomUUID();
        when(valueOps.get("2fa_login:token")).thenReturn(userId.toString());
        when(recoveryCodeRepository.findByUserIdAndUsedFalse(userId)).thenReturn(Collections.emptyList());

        assertThatThrownBy(() -> twoFactorService.useRecoveryCodeAndLogin("token", "AAAA-BBBB-CCCC-DDDD"))
                .isInstanceOf(RecoveryCodeExhaustedException.class);
    }

    @Test
    void deve_lancar_RecoveryCodeInvalidException_quando_codigo_nao_bate() {
        UUID userId = UUID.randomUUID();
        User user = buildUser(userId, true);

        RecoveryCode rc = RecoveryCode.builder()
                .user(user)
                .codeHash(bcrypt.encode("AAAA-BBBB-CCCC-DDDD"))
                .build();
        ReflectionTestUtils.invokeMethod(rc, "prePersist");

        when(valueOps.get("2fa_login:token")).thenReturn(userId.toString());
        when(recoveryCodeRepository.findByUserIdAndUsedFalse(userId)).thenReturn(List.of(rc));

        assertThatThrownBy(() ->
                twoFactorService.useRecoveryCodeAndLogin("token", "XXXX-XXXX-XXXX-XXXX"))
                .isInstanceOf(RecoveryCodeInvalidException.class);
    }

    @Test
    void deve_usar_recovery_code_valido_e_retornar_Success() {
        UUID userId = UUID.randomUUID();
        User user = buildUser(userId, true);

        String plainCode = "AAAA-BBBB-CCCC-DDDD";
        RecoveryCode rc = RecoveryCode.builder()
                .user(user)
                .codeHash(bcrypt.encode(plainCode))
                .build();
        ReflectionTestUtils.invokeMethod(rc, "prePersist");

        when(valueOps.get("2fa_login:token")).thenReturn(userId.toString());
        when(recoveryCodeRepository.findByUserIdAndUsedFalse(userId)).thenReturn(List.of(rc));
        when(recoveryCodeRepository.save(any())).thenReturn(rc);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(jwtTokenProvider.generateAccessToken(any(), any(), any(), any())).thenReturn("access.jwt");
        when(refreshTokenService.issue(any())).thenReturn("refresh-token");

        AuthResult result = twoFactorService.useRecoveryCodeAndLogin("token", plainCode);

        assertThat(result).isInstanceOf(AuthResult.Success.class);
        assertThat(rc.isUsed()).isTrue();
        verify(redis).delete("2fa_login:token");
    }

    // ─── helpers ──────────────────────────────────────────────────────────────

    private User buildUser(UUID id, boolean totpEnabled) {
        User user = User.builder()
                .email("ana@loja.com.br")
                .passwordHash(bcrypt.encode("Senha@1234"))
                .fullName("Ana Lima")
                .role(UserRole.CUSTOMER)
                .status(UserStatus.ACTIVE)
                .build();
        ReflectionTestUtils.invokeMethod(user, "prePersist");
        user.setTotpEnabled(totpEnabled);
        return user;
    }
}
