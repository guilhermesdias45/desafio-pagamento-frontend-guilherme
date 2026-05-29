package com.acaboumony.user.repository;

import com.acaboumony.user.domain.entity.User;
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
class UserRepositoryIT extends BaseIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    private User buildUser(String email) {
        return User.builder()
                .email(email)
                .passwordHash("$2a$12$hashedpassword1234567890123456789012345678901234")
                .fullName("Test User")
                .role(UserRole.CUSTOMER)
                .status(UserStatus.ACTIVE)
                .build();
    }

    @Test
    void deve_persistir_e_recuperar_user_quando_save_e_findById() {
        User saved = userRepository.save(buildUser("test@example.com"));

        Optional<User> found = userRepository.findById(saved.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getEmail()).isEqualTo("test@example.com");
        assertThat(found.get().getRole()).isEqualTo(UserRole.CUSTOMER);
    }

    @Test
    void deve_encontrar_user_por_email_quando_findByEmail() {
        userRepository.save(buildUser("find@example.com"));

        Optional<User> found = userRepository.findByEmail("find@example.com");

        assertThat(found).isPresent();
        assertThat(found.get().getFullName()).isEqualTo("Test User");
    }

    @Test
    void deve_retornar_optional_empty_quando_email_nao_existe() {
        Optional<User> found = userRepository.findByEmail("nonexistent@example.com");

        assertThat(found).isEmpty();
    }

    @Test
    void deve_falhar_quando_email_duplicado_quando_save() {
        userRepository.save(buildUser("dup@example.com"));
        userRepository.flush();

        assertThatThrownBy(() -> {
            userRepository.save(buildUser("dup@example.com"));
            userRepository.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }
}
