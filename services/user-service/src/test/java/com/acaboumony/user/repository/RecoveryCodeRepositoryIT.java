package com.acaboumony.user.repository;

import com.acaboumony.user.domain.entity.RecoveryCode;
import com.acaboumony.user.domain.entity.User;
import com.acaboumony.user.domain.enums.UserRole;
import com.acaboumony.user.domain.enums.UserStatus;
import com.acaboumony.user.support.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class RecoveryCodeRepositoryIT extends BaseIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RecoveryCodeRepository recoveryCodeRepository;

    private User savedUser(String email) {
        return userRepository.save(User.builder()
                .email(email)
                .passwordHash("$2a$12$hashedpassword1234567890123456789012345678901234")
                .fullName("User")
                .role(UserRole.CUSTOMER)
                .status(UserStatus.ACTIVE)
                .build());
    }

    @Test
    void deve_persistir_recovery_code_associado_a_user() {
        User user = savedUser("rc1@example.com");
        RecoveryCode rc = RecoveryCode.builder()
                .user(user)
                .codeHash("$2a$12$hashedrecovery1234567890123456789012345678901234")
                .build();

        RecoveryCode saved = recoveryCodeRepository.save(rc);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getUser().getId()).isEqualTo(user.getId());
        assertThat(saved.isUsed()).isFalse();
    }

    @Test
    void deve_listar_recovery_codes_nao_usados_por_user_quando_findByUserIdAndUsedFalse() {
        User user = savedUser("rc2@example.com");
        recoveryCodeRepository.save(RecoveryCode.builder().user(user)
                .codeHash("$2a$12$hash1234567890123456789012345678901234567890123456").build());
        RecoveryCode used = RecoveryCode.builder().user(user)
                .codeHash("$2a$12$hash2234567890123456789012345678901234567890123456").build();
        RecoveryCode savedUsed = recoveryCodeRepository.save(used);
        savedUsed.markUsed();
        recoveryCodeRepository.save(savedUsed);
        recoveryCodeRepository.flush();

        List<RecoveryCode> unused = recoveryCodeRepository.findByUserIdAndUsedFalse(user.getId());

        assertThat(unused).hasSize(1);
        assertThat(unused.get(0).isUsed()).isFalse();
    }

    @Test
    void deve_deletar_em_cascade_quando_user_deletado() {
        User user = savedUser("rc3@example.com");
        recoveryCodeRepository.save(RecoveryCode.builder().user(user)
                .codeHash("$2a$12$hash3234567890123456789012345678901234567890123456").build());
        recoveryCodeRepository.flush();

        userRepository.delete(user);
        userRepository.flush();

        List<RecoveryCode> remaining = recoveryCodeRepository.findByUserIdAndUsedFalse(user.getId());
        assertThat(remaining).isEmpty();
    }
}
