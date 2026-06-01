package com.acaboumony.user.service;

import com.acaboumony.user.domain.entity.Merchant;
import com.acaboumony.user.domain.entity.User;
import com.acaboumony.user.domain.enums.MerchantStatus;
import com.acaboumony.user.domain.enums.UserRole;
import com.acaboumony.user.domain.enums.UserStatus;
import com.acaboumony.user.exception.CnpjAlreadyRegisteredException;
import com.acaboumony.user.repository.MerchantRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MerchantServiceTest {

    @Mock MerchantRepository merchantRepository;
    @InjectMocks MerchantService merchantService;

    @Test
    void deve_criar_merchant_quando_cnpj_nao_existe() {
        User owner = buildUser();
        Merchant saved = buildMerchant(owner);
        when(merchantRepository.existsByCnpj("12345678000195")).thenReturn(false);
        when(merchantRepository.save(any())).thenReturn(saved);

        Merchant result = merchantService.createMerchant(owner, "Loja S.A.", "12345678000195");

        assertThat(result.getCompanyName()).isEqualTo("Loja S.A.");
        assertThat(result.getStatus()).isEqualTo(MerchantStatus.ACTIVE);
        verify(merchantRepository).save(any());
    }

    @Test
    void deve_lancar_CnpjAlreadyRegisteredException_quando_cnpj_ja_existe() {
        User owner = buildUser();
        when(merchantRepository.existsByCnpj("12345678000195")).thenReturn(true);

        assertThatThrownBy(() -> merchantService.createMerchant(owner, "Loja S.A.", "12345678000195"))
                .isInstanceOf(CnpjAlreadyRegisteredException.class);

        verify(merchantRepository, never()).save(any());
    }

    private User buildUser() {
        User user = User.builder()
                .email("dono@loja.com.br")
                .passwordHash("$2a$12$hash")
                .fullName("Dono")
                .role(UserRole.MERCHANT_OWNER)
                .status(UserStatus.ACTIVE)
                .build();
        ReflectionTestUtils.invokeMethod(user, "prePersist");
        return user;
    }

    private Merchant buildMerchant(User owner) {
        Merchant m = Merchant.builder()
                .companyName("Loja S.A.")
                .cnpj("12345678000195")
                .owner(owner)
                .status(MerchantStatus.ACTIVE)
                .build();
        ReflectionTestUtils.invokeMethod(m, "prePersist");
        return m;
    }
}
