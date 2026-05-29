package com.acaboumony.user.service;

import com.acaboumony.user.support.BaseIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class LoginAttemptServiceIT extends BaseIntegrationTest {

    @Autowired
    private LoginAttemptService loginAttemptService;

    @Autowired
    private StringRedisTemplate redis;

    /** Use unique email per test to avoid cross-test state contamination. */
    private String email() {
        return "test-" + UUID.randomUUID() + "@example.com";
    }

    @AfterEach
    void cleanUp() {
        // Clean up test keys (best-effort)
        redis.keys("login_attempts:*").forEach(redis::delete);
        redis.keys("account_locked:*").forEach(redis::delete);
    }

    @Test
    void deve_retornar_zero_quando_email_nunca_tentou_login() {
        String email = email();

        assertThat(loginAttemptService.isLocked(email)).isFalse();
        assertThat(loginAttemptService.getUnlockAt(email)).isEmpty();
    }

    @Test
    void deve_incrementar_contador_quando_recordFailure_chamado() {
        String email = email();

        LoginAttemptService.LoginAttemptResult r1 = loginAttemptService.recordFailure(email);
        LoginAttemptService.LoginAttemptResult r2 = loginAttemptService.recordFailure(email);

        assertThat(r1.attempts()).isEqualTo(1);
        assertThat(r2.attempts()).isEqualTo(2);
        assertThat(r1.nowLocked()).isFalse();
        assertThat(r2.nowLocked()).isFalse();
    }

    @Test
    void deve_bloquear_conta_e_retornar_unlockAt_quando_5_tentativas_falhas() {
        // CE-LOGIN-001
        String email = email();

        LoginAttemptService.LoginAttemptResult result = null;
        for (int i = 0; i < 5; i++) {
            result = loginAttemptService.recordFailure(email);
        }

        assertThat(result).isNotNull();
        assertThat(result.nowLocked()).isTrue();
        assertThat(result.unlockAt()).isNotNull();
        assertThat(result.unlockAt()).isAfter(Instant.now());
    }

    @Test
    void deve_indicar_conta_bloqueada_quando_isLocked_chamado_apos_5_falhas() {
        // CE-LOGIN-002
        String email = email();
        for (int i = 0; i < 5; i++) {
            loginAttemptService.recordFailure(email);
        }

        assertThat(loginAttemptService.isLocked(email)).isTrue();
    }

    @Test
    void deve_zerar_contador_quando_recordSuccess_chamado() {
        String email = email();
        loginAttemptService.recordFailure(email);
        loginAttemptService.recordFailure(email);

        loginAttemptService.recordSuccess(email);

        assertThat(loginAttemptService.isLocked(email)).isFalse();
        // After success the counter key is gone
        assertThat(redis.hasKey("login_attempts:" + email)).isFalse();
    }

    @Test
    void deve_retornar_unlockAt_em_iso8601_quando_getUnlockAt_em_conta_bloqueada() {
        String email = email();
        for (int i = 0; i < 5; i++) {
            loginAttemptService.recordFailure(email);
        }

        Optional<Instant> unlockAt = loginAttemptService.getUnlockAt(email);

        assertThat(unlockAt).isPresent();
        assertThat(unlockAt.get()).isAfter(Instant.now());
        // Verify it round-trips through ISO-8601
        assertThat(unlockAt.get().toString()).matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\..*Z");
    }

    @Test
    void deve_nao_bloquear_antes_de_atingir_max_attempts() {
        String email = email();
        for (int i = 0; i < 4; i++) {
            LoginAttemptService.LoginAttemptResult r = loginAttemptService.recordFailure(email);
            assertThat(r.nowLocked()).isFalse();
        }
        assertThat(loginAttemptService.isLocked(email)).isFalse();
    }
}
