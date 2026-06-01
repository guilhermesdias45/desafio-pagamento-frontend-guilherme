package com.acaboumony.user.service;

import com.acaboumony.user.config.TotpProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AesGcmCryptoServiceTest {

    // Valid 64-hex-char key (32 bytes)
    private static final String VALID_KEY = "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f";

    AesGcmCryptoService service;

    @BeforeEach
    void setUp() {
        TotpProperties props = mock(TotpProperties.class);
        when(props.aesKey()).thenReturn(VALID_KEY);
        service = new AesGcmCryptoService(props);
        service.init();
    }

    // ─── encrypt / decrypt ────────────────────────────────────────────────────

    @Test
    void deve_criptografar_e_descriptografar_corretamente() {
        String plaintext = "JBSWY3DPEHPK3PXP";

        String encrypted = service.encrypt(plaintext);
        String decrypted = service.decrypt(encrypted);

        assertThat(decrypted).isEqualTo(plaintext);
    }

    @Test
    void deve_criptografar_string_vazia() {
        String encrypted = service.encrypt("");
        assertThat(service.decrypt(encrypted)).isEmpty();
    }

    @Test
    void deve_criptografar_texto_longo() {
        String longText = "A".repeat(1000);
        assertThat(service.decrypt(service.encrypt(longText))).isEqualTo(longText);
    }

    @Test
    void deve_criptografar_caracteres_especiais_e_unicode() {
        String text = "secret-αβγδ-€-🔑";
        assertThat(service.decrypt(service.encrypt(text))).isEqualTo(text);
    }

    @RepeatedTest(5)
    void deve_gerar_ciphertexts_diferentes_para_o_mesmo_plaintext() {
        String p = "JBSWY3DPEHPK3PXP";
        String c1 = service.encrypt(p);
        String c2 = service.encrypt(p);
        // IV is random — same plaintext must produce different ciphertexts
        assertThat(c1).isNotEqualTo(c2);
    }

    // ─── decrypt — casos de erro ──────────────────────────────────────────────

    @Test
    void deve_lancar_excecao_quando_ciphertext_adulterado() {
        String encrypted = service.encrypt("segredo");
        // Flip last character to corrupt the GCM auth tag
        String corrupted = encrypted.substring(0, encrypted.length() - 1) + "X";

        assertThatThrownBy(() -> service.decrypt(corrupted))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Decryption failed");
    }

    @Test
    void deve_lancar_excecao_quando_base64_invalido() {
        assertThatThrownBy(() -> service.decrypt("not-valid-base64!!!"))
                .isInstanceOf(IllegalStateException.class);
    }

    // ─── init — chave inválida ─────────────────────────────────────────────────

    @Test
    void deve_lancar_excecao_quando_chave_aes_com_tamanho_errado() {
        TotpProperties props = mock(TotpProperties.class);
        when(props.aesKey()).thenReturn("0102030405060708"); // only 8 bytes — invalid
        AesGcmCryptoService badService = new AesGcmCryptoService(props);

        assertThatThrownBy(badService::init)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes");
    }
}
