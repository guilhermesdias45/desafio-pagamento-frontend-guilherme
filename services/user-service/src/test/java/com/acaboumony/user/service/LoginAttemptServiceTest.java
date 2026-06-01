package com.acaboumony.user.service;

import com.acaboumony.user.config.SecurityLoginProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LoginAttemptServiceTest {

    @Mock StringRedisTemplate redis;
    @Mock ValueOperations<String, String> valueOps;
    @Mock SecurityLoginProperties props;

    LoginAttemptService service;

    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(props.maxAttempts()).thenReturn(5);
        when(props.lockoutDurationMinutes()).thenReturn(30);
        service = new LoginAttemptService(redis, props);
    }

    // ─── recordFailure ────────────────────────────────────────────────────────

    @Test
    void deve_incrementar_contador_e_retornar_resultado_sem_bloqueio() {
        when(valueOps.increment("login_attempts:ana@loja.com.br")).thenReturn(2L);

        LoginAttemptService.LoginAttemptResult result = service.recordFailure("ana@loja.com.br");

        assertThat(result.attempts()).isEqualTo(2);
        assertThat(result.nowLocked()).isFalse();
        assertThat(result.unlockAt()).isNull();
    }

    @Test
    void deve_definir_TTL_na_primeira_tentativa() {
        when(valueOps.increment("login_attempts:ana@loja.com.br")).thenReturn(1L);

        service.recordFailure("ana@loja.com.br");

        verify(redis).expire(eq("login_attempts:ana@loja.com.br"), any());
    }

    @Test
    void deve_bloquear_conta_quando_atinge_maximo_de_tentativas() {
        when(valueOps.increment(anyString())).thenReturn(5L);

        LoginAttemptService.LoginAttemptResult result = service.recordFailure("ana@loja.com.br");

        assertThat(result.nowLocked()).isTrue();
        assertThat(result.unlockAt()).isNotNull();
        verify(valueOps).set(eq("account_locked:ana@loja.com.br"), anyString(), any());
    }

    @Test
    void deve_bloquear_quando_acima_do_maximo() {
        when(valueOps.increment(anyString())).thenReturn(10L);

        LoginAttemptService.LoginAttemptResult result = service.recordFailure("ana@loja.com.br");

        assertThat(result.nowLocked()).isTrue();
    }

    // ─── recordSuccess ────────────────────────────────────────────────────────

    @Test
    void deve_apagar_chaves_de_tentativa_e_bloqueio_no_sucesso() {
        service.recordSuccess("ana@loja.com.br");

        verify(redis).delete("login_attempts:ana@loja.com.br");
        verify(redis).delete("account_locked:ana@loja.com.br");
    }

    // ─── isLocked ─────────────────────────────────────────────────────────────

    @Test
    void deve_retornar_true_quando_conta_bloqueada() {
        when(redis.hasKey("account_locked:ana@loja.com.br")).thenReturn(true);

        assertThat(service.isLocked("ana@loja.com.br")).isTrue();
    }

    @Test
    void deve_retornar_false_quando_conta_nao_bloqueada() {
        when(redis.hasKey("account_locked:ana@loja.com.br")).thenReturn(false);

        assertThat(service.isLocked("ana@loja.com.br")).isFalse();
    }

    // ─── getUnlockAt ──────────────────────────────────────────────────────────

    @Test
    void deve_retornar_Optional_empty_quando_nao_bloqueado() {
        when(valueOps.get("account_locked:ana@loja.com.br")).thenReturn(null);

        assertThat(service.getUnlockAt("ana@loja.com.br")).isEmpty();
    }

    @Test
    void deve_retornar_Instant_quando_bloqueado() {
        Instant unlockAt = Instant.now().plusSeconds(1800);
        when(valueOps.get("account_locked:ana@loja.com.br")).thenReturn(unlockAt.toString());

        Optional<Instant> result = service.getUnlockAt("ana@loja.com.br");

        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(unlockAt);
    }

    @Test
    void deve_retornar_Optional_empty_quando_valor_invalido_no_redis() {
        when(valueOps.get("account_locked:ana@loja.com.br")).thenReturn("valor-invalido");

        assertThat(service.getUnlockAt("ana@loja.com.br")).isEmpty();
    }
}
