package com.acaboumony.user.repository;

import com.acaboumony.user.domain.entity.Merchant;
import com.acaboumony.user.domain.entity.User;
import com.acaboumony.user.domain.enums.MerchantStatus;
import com.acaboumony.user.domain.enums.UserRole;
import com.acaboumony.user.domain.enums.UserStatus;
import com.acaboumony.user.support.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Transactional
class MerchantRepositoryIT extends BaseIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MerchantRepository merchantRepository;

    private User saveOwner(String email) {
        return userRepository.save(User.builder()
                .email(email)
                .passwordHash("$2a$12$hashedpassword1234567890123456789012345678901234")
                .fullName("Owner")
                .role(UserRole.MERCHANT_OWNER)
                .status(UserStatus.ACTIVE)
                .build());
    }

    @Test
    void deve_persistir_merchant_com_owner_quando_save() {
        User owner = saveOwner("owner@merchant.com");
        Merchant merchant = Merchant.builder()
                .companyName("Loja da Ana")
                .cnpj("11222333000181")
                .owner(owner)
                .status(MerchantStatus.ACTIVE)
                .build();

        Merchant saved = merchantRepository.save(merchant);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getOwner().getId()).isEqualTo(owner.getId());
        assertThat(saved.getCnpj()).isEqualTo("11222333000181");
    }

    @Test
    void deve_encontrar_merchant_por_cnpj_quando_findByCnpj() {
        User owner = saveOwner("owner2@merchant.com");
        merchantRepository.save(Merchant.builder()
                .companyName("Loja Teste")
                .cnpj("60701190000104")
                .owner(owner)
                .build());

        Optional<Merchant> found = merchantRepository.findByCnpj("60701190000104");

        assertThat(found).isPresent();
        assertThat(found.get().getCompanyName()).isEqualTo("Loja Teste");
    }

    @Test
    void deve_falhar_quando_cnpj_duplicado_quando_save() {
        User owner1 = saveOwner("owner3@merchant.com");
        User owner2 = saveOwner("owner4@merchant.com");
        merchantRepository.save(Merchant.builder()
                .companyName("Loja A")
                .cnpj("11222333000181")
                .owner(owner1)
                .build());
        merchantRepository.flush();

        assertThatThrownBy(() -> {
            merchantRepository.save(Merchant.builder()
                    .companyName("Loja B")
                    .cnpj("11222333000181")
                    .owner(owner2)
                    .build());
            merchantRepository.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deve_persistir_merchant_e_atualizar_user_merchant_id_quando_save_em_transacao() {
        User owner = saveOwner("owner5@merchant.com");
        Merchant merchant = merchantRepository.save(Merchant.builder()
                .companyName("Loja Round Trip")
                .cnpj("11222333000181")
                .owner(owner)
                .build());

        // Update user to point to merchant (simulating circular FK round-trip)
        owner.setMerchant(merchant);
        userRepository.save(owner);
        userRepository.flush();

        User reloaded = userRepository.findById(owner.getId()).orElseThrow();
        assertThat(reloaded.getMerchant()).isNotNull();
        assertThat(reloaded.getMerchant().getId()).isEqualTo(merchant.getId());
    }
}
