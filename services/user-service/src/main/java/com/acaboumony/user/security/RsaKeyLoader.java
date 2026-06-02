package com.acaboumony.user.security;

import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.regex.Pattern;

/**
 * Utility for decoding base64-encoded PEM keys (with or without PEM headers/footers).
 * Supports PKCS#1 (BEGIN RSA PRIVATE KEY) and PKCS#8 (BEGIN PRIVATE KEY or raw base64).
 */
public final class RsaKeyLoader {

    private static final Pattern HEADER_PATTERN = Pattern.compile("-----[A-Z ]+-----");

    // AlgorithmIdentifier for rsaEncryption: SEQUENCE { OID 1.2.840.113549.1.1.1, NULL }
    private static final byte[] RSA_ALGO_ID = {
        0x30, 0x0d,
        0x06, 0x09, 0x2a, (byte)0x86, 0x48, (byte)0x86, (byte)0xf7, 0x0d, 0x01, 0x01, 0x01,
        0x05, 0x00
    };

    private RsaKeyLoader() {}

    public static PrivateKey loadPrivateKey(String base64Pem) {
        try {
            byte[] decoded = decode(base64Pem);
            if (isPkcs1(base64Pem)) {
                decoded = wrapPkcs1ToPkcs8(decoded);
            }
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(decoded));
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to load RSA private key", e);
        }
    }

    public static PublicKey loadPublicKey(String base64Pem) {
        try {
            byte[] decoded = decode(base64Pem);
            return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(decoded));
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to load RSA public key", e);
        }
    }

    private static boolean isPkcs1(String base64Pem) {
        return base64Pem.replace("\\n", "\n").contains("BEGIN RSA PRIVATE KEY");
    }

    private static byte[] decode(String base64Pem) {
        String normalized = base64Pem.replace("\\n", "\n");
        String clean = HEADER_PATTERN.matcher(normalized).replaceAll("");
        clean = clean.replaceAll("\\s+", "");
        return Base64.getDecoder().decode(clean);
    }

    // PrivateKeyInfo (PKCS#8) ::= SEQUENCE { version INTEGER 0, AlgorithmIdentifier, OCTET STRING { pkcs1 } }
    private static byte[] wrapPkcs1ToPkcs8(byte[] pkcs1) {
        byte[] version     = {0x02, 0x01, 0x00};
        byte[] octetString = concat(new byte[]{0x04}, asn1Length(pkcs1.length), pkcs1);
        byte[] content     = concat(version, RSA_ALGO_ID, octetString);
        return concat(new byte[]{0x30}, asn1Length(content.length), content);
    }

    private static byte[] asn1Length(int len) {
        if (len < 128) return new byte[]{(byte) len};
        if (len < 256) return new byte[]{(byte) 0x81, (byte) len};
        return new byte[]{(byte) 0x82, (byte)(len >> 8), (byte) len};
    }

    private static byte[] concat(byte[]... arrays) {
        int total = 0;
        for (byte[] a : arrays) total += a.length;
        byte[] result = new byte[total];
        int pos = 0;
        for (byte[] a : arrays) { System.arraycopy(a, 0, result, pos, a.length); pos += a.length; }
        return result;
    }
}
