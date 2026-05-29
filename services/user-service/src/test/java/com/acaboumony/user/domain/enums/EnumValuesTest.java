package com.acaboumony.user.domain.enums;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EnumValuesTest {

    @Test
    void deve_ter_3_valores_em_UserRole_quando_invocado_values() {
        UserRole[] values = UserRole.values();
        assertThat(values).hasSize(3);
        assertThat(values).containsExactly(
                UserRole.CUSTOMER,
                UserRole.MERCHANT_OWNER,
                UserRole.STAFF);
    }

    @Test
    void deve_ter_4_valores_em_UserStatus_quando_invocado_values() {
        UserStatus[] values = UserStatus.values();
        assertThat(values).hasSize(4);
        assertThat(values).containsExactly(
                UserStatus.PENDING_EMAIL_CONFIRMATION,
                UserStatus.ACTIVE,
                UserStatus.LOCKED,
                UserStatus.DISABLED);
    }

    @Test
    void deve_ter_3_valores_em_MerchantStatus_quando_invocado_values() {
        MerchantStatus[] values = MerchantStatus.values();
        assertThat(values).hasSize(3);
        assertThat(values).containsExactly(
                MerchantStatus.ACTIVE,
                MerchantStatus.SUSPENDED,
                MerchantStatus.INACTIVE);
    }

    @Test
    void deve_retornar_false_para_canPurchase_apenas_para_STAFF() {
        assertThat(UserRole.CUSTOMER.canPurchase()).isTrue();
        assertThat(UserRole.MERCHANT_OWNER.canPurchase()).isTrue();
        assertThat(UserRole.STAFF.canPurchase()).isFalse();
    }
}
