package com.acaboumony.payment.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.SecureRandom;
import java.util.Base64;

@Service
public class MpEncryptionService {

    private static final Logger log = LoggerFactory.getLogger(MpEncryptionService.class);
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;

    private final Key key;
    private final SecureRandom secureRandom;

    public MpEncryptionService(Key mpEncryptionKey) {
        this.key = mpEncryptionKey;
        this.secureRandom = new SecureRandom();
        if (key == null) {
            log.warn("No encryption key configured — tokens will be stored in plaintext");
        }
    }

    public String encrypt(String plainText) {
        if (plainText == null) return null;
        if (key == null) {
            log.warn("Encryption key not available, returning plaintext");
            return plainText;
        }
        try {
            var iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);
            var cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            var cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            var combined = ByteBuffer.allocate(iv.length + cipherText.length)
                .put(iv).put(cipherText).array();
            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            log.error("Failed to encrypt data", e);
            throw new RuntimeException("Encryption failed", e);
        }
    }

    public String decrypt(String encryptedText) {
        if (encryptedText == null) return null;
        if (key == null) {
            log.warn("Encryption key not available, returning ciphertext as-is");
            return encryptedText;
        }
        try {
            var combined = Base64.getDecoder().decode(encryptedText);
            var iv = new byte[GCM_IV_LENGTH];
            var cipherText = new byte[combined.length - GCM_IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH);
            System.arraycopy(combined, GCM_IV_LENGTH, cipherText, 0, cipherText.length);
            var cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            return new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("Failed to decrypt data", e);
            throw new RuntimeException("Decryption failed", e);
        }
    }
}
