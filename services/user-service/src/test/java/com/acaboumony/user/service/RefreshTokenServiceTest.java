package com.acaboumony.user.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock StringRedisTemplate redis;
    @Mock ValueOperations<String, String> valueOps;

    RefreshTokenService service;

    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(valueOps);
        service = new RefreshTokenService(redis);
    }

    // ─── issue ────────────────────────────────────────────────────────────────

    @Test
    void deve_emitir_token_e_armazenar_duas_chaves_no_redis() {
        UUID userId = UUID.randomUUID();

        String token = service.issue(userId);

        assertThat(token).isNotBlank();
        // primary key: refresh_token:{userId}:{token}
        verify(valueOps).set(
                org.mockito.ArgumentMatchers.contains("refresh_token:" + userId),
                anyString(),
                any());
        // lookup key: refresh_token_lookup:{token}
        verify(valueOps).set(
                org.mockito.ArgumentMatchers.contains("refresh_token_lookup:"),
                anyString(),
                any());
    }

    @Test
    void deve_gerar_token_diferente_a_cada_chamada() {
        UUID userId = UUID.randomUUID();
        String t1 = service.issue(userId);
        String t2 = service.issue(userId);

        assertThat(t1).isNotEqualTo(t2);
    }

    // ─── validateAndDelete ────────────────────────────────────────────────────

    @Test
    void deve_retornar_userId_e_deletar_chaves_quando_token_valido() {
        UUID userId = UUID.randomUUID();
        String token = "test-token";

        when(valueOps.get("refresh_token_lookup:" + token)).thenReturn(userId.toString());

        Optional<UUID> result = service.validateAndDelete(token);

        assertThat(result).isPresent().contains(userId);
        verify(redis).delete("refresh_token_lookup:" + token);
        verify(redis).delete("refresh_token:" + userId + ":" + token);
    }

    @Test
    void deve_retornar_empty_quando_token_nao_existe() {
        when(valueOps.get(anyString())).thenReturn(null);

        Optional<UUID> result = service.validateAndDelete("unknown-token");

        assertThat(result).isEmpty();
        verify(redis, never()).delete(anyString());
    }

    @Test
    void deve_retornar_empty_quando_token_ja_rotacionado() {
        when(valueOps.get("refresh_token_lookup:old-token")).thenReturn(null);

        Optional<UUID> result = service.validateAndDelete("old-token");

        assertThat(result).isEmpty();
    }

    // ─── revoke ───────────────────────────────────────────────────────────────

    @Test
    void deve_deletar_ambas_as_chaves_quando_revogar_token_valido() {
        UUID userId = UUID.randomUUID();
        String token = "to-revoke";

        when(valueOps.get("refresh_token_lookup:" + token)).thenReturn(userId.toString());

        service.revoke(token);

        verify(redis).delete("refresh_token_lookup:" + token);
        verify(redis).delete("refresh_token:" + userId + ":" + token);
    }

    @Test
    void deve_ser_idempotente_quando_token_ja_revogado() {
        when(valueOps.get(anyString())).thenReturn(null);

        service.revoke("already-gone");

        verify(redis, never()).delete(anyString());
    }
}
