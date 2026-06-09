package com.acaboumony.payment.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.spec.SecretKeySpec;

import static org.junit.jupiter.api.Assertions.*;

class MpEncryptionServiceTest {

    private MpEncryptionService service;

    @BeforeEach
    void setUp() {
        var key = new SecretKeySpec(new byte[32], "AES");
        service = new MpEncryptionService(key);
    }

    @Test
    void encryptDecrypt_roundtrip() {
        var original = "test_access_token_12345";
        var encrypted = service.encrypt(original);
        var decrypted = service.decrypt(encrypted);
        assertEquals(original, decrypted);
    }

    @Test
    void encrypt_outputIsBase64() {
        var result = service.encrypt("hello");
        assertNotNull(result);
        assertTrue(result.matches("^[A-Za-z0-9+/=]+$"));
    }

    @Test
    void encrypt_sameInput_producesDifferentOutput() {
        var input = "same_value";
        var result1 = service.encrypt(input);
        var result2 = service.encrypt(input);
        assertNotEquals(result1, result2);
    }

    @Test
    void decrypt_roundtrip_specialChars() {
        var input = "token_with_special_chars!@#$%^&*()_+-=[]{}|;':\",./<>?`~";
        var encrypted = service.encrypt(input);
        var decrypted = service.decrypt(encrypted);
        assertEquals(input, decrypted);
    }

    @Test
    void encrypt_null_returnsNull() {
        assertNull(service.encrypt(null));
    }

    @Test
    void decrypt_null_returnsNull() {
        assertNull(service.decrypt(null));
    }

    @Test
    void encryptDecrypt_longString() {
        var input = "a".repeat(1000);
        var encrypted = service.encrypt(input);
        var decrypted = service.decrypt(encrypted);
        assertEquals(input, decrypted);
    }

    @Test
    void decrypt_invalidString_throwsException() {
        assertThrows(RuntimeException.class, () -> service.decrypt("invalid_base64!!!"));
    }
}
