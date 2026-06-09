package com.acaboumony.payment.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.crypto.spec.SecretKeySpec;
import java.security.Key;

@Configuration
public class MpEncryptionConfig {

    @Value("${mercadopago.encryption.key}")
    private String encryptionKey;

    @Bean
    Key mpEncryptionKey() {
        if (encryptionKey == null || encryptionKey.isBlank()) {
            return null;
        }
        var hex = encryptionKey.length() == 32 ? encryptionKey + encryptionKey : encryptionKey;
        var raw = hex.length() >= 64
            ? hex.substring(0, 64)
            : hex.repeat(64 / hex.length() + 1).substring(0, 64);
        var bytes = new byte[32];
        for (int i = 0; i < 32; i++) {
            bytes[i] = (byte) Integer.parseInt(raw.substring(i * 2, i * 2 + 2), 16);
        }
        return new SecretKeySpec(bytes, "AES");
    }
}
