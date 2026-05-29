package com.acaboumony.user.service;

import com.acaboumony.user.config.TotpProperties;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;

/**
 * AES-256-GCM encryption service for TOTP secrets at rest.
 *
 * <p>Format: {@code Base64(IV[12] || Ciphertext[n] || Tag[16])}.</p>
 * <p>IV is randomly generated per encryption call — same plaintext produces different ciphertexts.</p>
 * <p>GCM authentication tag (128 bits) detects any tampering with the ciphertext.</p>
 */
@Service
public class AesGcmCryptoService {

    private static final int GCM_IV_LENGTH  = 12;
    private static final int GCM_TAG_LENGTH = 128; // bits

    private final TotpProperties totpProperties;
    private SecretKey secretKey;

    public AesGcmCryptoService(TotpProperties totpProperties) {
        this.totpProperties = totpProperties;
    }

    @PostConstruct
    void init() {
        byte[] keyBytes = HexFormat.of().parseHex(totpProperties.aesKey());
        if (keyBytes.length != 32) {
            throw new IllegalStateException("AES key must be exactly 32 bytes (64 hex chars)");
        }
        this.secretKey = new SecretKeySpec(keyBytes, "AES");
    }

    /**
     * Encrypts {@code plaintext} with AES-256-GCM.
     *
     * @param plaintext UTF-8 string to encrypt
     * @return base64-encoded string: IV + ciphertext + GCM tag
     */
    public String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(java.nio.charset.StandardCharsets.UTF_8));

            byte[] output = ByteBuffer.allocate(iv.length + ciphertext.length)
                    .put(iv)
                    .put(ciphertext)
                    .array();
            return Base64.getEncoder().encodeToString(output);
        } catch (Exception e) {
            throw new IllegalStateException("Encryption failed", e);
        }
    }

    /**
     * Decrypts a base64-encoded ciphertext produced by {@link #encrypt(String)}.
     *
     * @param base64 base64-encoded IV + ciphertext + GCM tag
     * @return original plaintext
     * @throws IllegalStateException if decryption fails (e.g. tampered ciphertext)
     */
    public String decrypt(String base64) {
        try {
            byte[] input = Base64.getDecoder().decode(base64);
            byte[] iv         = Arrays.copyOfRange(input, 0, GCM_IV_LENGTH);
            byte[] ciphertext = Arrays.copyOfRange(input, GCM_IV_LENGTH, input.length);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] plaintext = cipher.doFinal(ciphertext);
            return new String(plaintext, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Decryption failed — data may be corrupted or tampered", e);
        }
    }
}
