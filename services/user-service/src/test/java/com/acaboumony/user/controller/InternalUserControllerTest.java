package com.acaboumony.user.controller;

import com.acaboumony.user.config.InternalSecretProperties;
import com.acaboumony.user.config.SecurityConfig;
import com.acaboumony.user.config.TestSecurityConfig;
import com.acaboumony.user.domain.entity.User;
import com.acaboumony.user.domain.enums.UserRole;
import com.acaboumony.user.domain.enums.UserStatus;
import com.acaboumony.user.exception.UserNotFoundException;
import com.acaboumony.user.repository.UserRepository;
import com.acaboumony.user.security.JwtTokenValidator;
import org.springframework.test.util.ReflectionTestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// CE-001: usuário existe → 200 com { id, email, role }
// CE-002: usuário não existe → 404 USER_NOT_FOUND
// CE-003/004: secret inválido → 401 (coberto por InternalAuthControllerTest via filter)
// CE-005: UUID inválido → 400
@WebMvcTest(
        controllers = InternalUserController.class,
        excludeAutoConfiguration = SecurityAutoConfiguration.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SecurityConfig.class)
)
@Import({GlobalExceptionHandler.class, TestSecurityConfig.class})
class InternalUserControllerTest {

    static final String TEST_SECRET = "test-internal-secret";

    @Autowired MockMvc mvc;
    @MockBean UserRepository userRepository;
    @MockBean InternalSecretProperties internalSecretProperties;
    @MockBean JwtTokenValidator jwtTokenValidator;

    private UUID customerId;
    private User user;

    @BeforeEach
    void setUp() {
        when(internalSecretProperties.secret()).thenReturn(TEST_SECRET);

        customerId = UUID.randomUUID();
        user = User.builder()
                .email("ana@loja.com")
                .passwordHash("$2a$10$hash")
                .fullName("Ana Lima")
                .role(UserRole.CUSTOMER)
                .status(UserStatus.ACTIVE)
                .build();
        ReflectionTestUtils.setField(user, "id", customerId);
    }

    @Test
    void CE001_deve_retornar_200_com_dados_quando_usuario_existe() throws Exception {
        when(userRepository.findById(customerId)).thenReturn(Optional.of(user));

        mvc.perform(get("/internal/users/{customerId}", customerId)
                        .header("X-Internal-Secret", TEST_SECRET))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("ana@loja.com"))
                .andExpect(jsonPath("$.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.id").value(customerId.toString()));
    }

    @Test
    void CE002_deve_retornar_404_quando_usuario_nao_existe() throws Exception {
        when(userRepository.findById(customerId)).thenReturn(Optional.empty());

        mvc.perform(get("/internal/users/{customerId}", customerId)
                        .header("X-Internal-Secret", TEST_SECRET))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("USER_NOT_FOUND"));
    }

    @Test
    void CE006_deve_retornar_200_mesmo_quando_usuario_esta_bloqueado() throws Exception {
        User blocked = User.builder()
                .email("bloqueado@loja.com")
                .passwordHash("$2a$10$hash")
                .fullName("Bloqueado")
                .role(UserRole.CUSTOMER)
                .status(UserStatus.LOCKED)
                .build();
        when(userRepository.findById(customerId)).thenReturn(Optional.of(blocked));

        mvc.perform(get("/internal/users/{customerId}", customerId)
                        .header("X-Internal-Secret", TEST_SECRET))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("CUSTOMER"));
    }
}
