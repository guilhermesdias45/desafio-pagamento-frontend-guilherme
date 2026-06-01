package com.acaboumony.user.service;

import com.acaboumony.user.domain.entity.Merchant;
import com.acaboumony.user.domain.entity.User;
import com.acaboumony.user.domain.enums.MerchantStatus;
import com.acaboumony.user.domain.enums.UserRole;
import com.acaboumony.user.domain.enums.UserStatus;
import com.acaboumony.user.dto.response.UserProfileResponse;
import com.acaboumony.user.exception.UserNotFoundException;
import com.acaboumony.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock UserRepository userRepository;
    @InjectMocks UserService userService;

    // ─── getProfile ───────────────────────────────────────────────────────────

    @Test
    void deve_retornar_UserProfileResponse_quando_usuario_existe() {
        UUID userId = UUID.randomUUID();
        User user = buildUser(userId, UserRole.CUSTOMER, false);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        UserProfileResponse resp = userService.getProfile(userId);

        assertThat(resp.userId()).isEqualTo(user.getId());
        assertThat(resp.email()).isEqualTo("ana@loja.com.br");
        assertThat(resp.role()).isEqualTo("CUSTOMER");
        assertThat(resp.merchantId()).isNull();
        assertThat(resp.twoFactorEnabled()).isFalse();
    }

    @Test
    void deve_incluir_merchantId_quando_usuario_e_MERCHANT_OWNER() {
        UUID userId = UUID.randomUUID();
        User user = buildUser(userId, UserRole.MERCHANT_OWNER, false);

        Merchant merchant = Merchant.builder()
                .companyName("Loja S.A.")
                .cnpj("12345678000195")
                .owner(user)
                .status(MerchantStatus.ACTIVE)
                .build();
        ReflectionTestUtils.invokeMethod(merchant, "prePersist");
        user.setMerchant(merchant);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        UserProfileResponse resp = userService.getProfile(userId);

        assertThat(resp.merchantId()).isEqualTo(merchant.getId());
    }

    @Test
    void deve_lancar_UserNotFoundException_quando_usuario_nao_existe_no_getProfile() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getProfile(userId))
                .isInstanceOf(UserNotFoundException.class);
    }

    // ─── updateFullName ───────────────────────────────────────────────────────

    @Test
    void deve_atualizar_nome_e_retornar_perfil_atualizado() {
        UUID userId = UUID.randomUUID();
        User user = buildUser(userId, UserRole.CUSTOMER, false);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenReturn(user);

        UserProfileResponse resp = userService.updateFullName(userId, "Ana Maria Lima");

        assertThat(user.getFullName()).isEqualTo("Ana Maria Lima");
        assertThat(resp.fullName()).isEqualTo("Ana Maria Lima");
        verify(userRepository).save(user);
    }

    @Test
    void deve_lancar_UserNotFoundException_quando_usuario_nao_existe_no_update() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.updateFullName(userId, "Novo Nome"))
                .isInstanceOf(UserNotFoundException.class);
    }

    // ─── helper ───────────────────────────────────────────────────────────────

    private User buildUser(UUID id, UserRole role, boolean totpEnabled) {
        User user = User.builder()
                .email("ana@loja.com.br")
                .passwordHash("$2a$12$hash")
                .fullName("Ana Lima")
                .role(role)
                .status(UserStatus.ACTIVE)
                .build();
        ReflectionTestUtils.invokeMethod(user, "prePersist");
        user.setTotpEnabled(totpEnabled);
        return user;
    }
}
