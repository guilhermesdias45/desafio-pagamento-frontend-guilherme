package com.acaboumony.user.security;

import org.junit.jupiter.api.Test;

import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.util.Arrays;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RsaKeyLoaderTest {

    @Test
    void deve_carregar_chave_privada_pkcs8_raw_base64() throws Exception {
        var keyPair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        String pkcs8Base64 = Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded());

        PrivateKey loaded = RsaKeyLoader.loadPrivateKey(pkcs8Base64);

        assertThat(loaded).isNotNull();
        assertThat(loaded.getAlgorithm()).isEqualTo("RSA");
    }

    @Test
    void deve_carregar_chave_privada_pkcs1_com_headers_e_backslash_n() throws Exception {
        var gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(1024);
        var keyPair = gen.generateKeyPair();

        byte[] pkcs1 = extractPkcs1FromPkcs8(keyPair.getPrivate().getEncoded());
        String pem = "-----BEGIN RSA PRIVATE KEY-----\\n"
                + Base64.getEncoder().encodeToString(pkcs1)
                + "\\n-----END RSA PRIVATE KEY-----";

        PrivateKey loaded = RsaKeyLoader.loadPrivateKey(pem);

        assertThat(loaded).isNotNull();
        assertThat(loaded.getAlgorithm()).isEqualTo("RSA");
    }

    @Test
    void deve_assinar_e_verificar_com_chave_pkcs1_carregada() throws Exception {
        var gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(1024);
        var keyPair = gen.generateKeyPair();

        byte[] pkcs1 = extractPkcs1FromPkcs8(keyPair.getPrivate().getEncoded());
        String privatePem = "-----BEGIN RSA PRIVATE KEY-----\\n"
                + Base64.getEncoder().encodeToString(pkcs1)
                + "\\n-----END RSA PRIVATE KEY-----";
        String publicBase64 = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());

        PrivateKey privateKey = RsaKeyLoader.loadPrivateKey(privatePem);
        PublicKey publicKey   = RsaKeyLoader.loadPublicKey(publicBase64);

        byte[] data = "payload-de-teste".getBytes();
        Signature signer = Signature.getInstance("SHA256withRSA");
        signer.initSign(privateKey);
        signer.update(data);
        byte[] sig = signer.sign();

        Signature verifier = Signature.getInstance("SHA256withRSA");
        verifier.initVerify(publicKey);
        verifier.update(data);
        assertThat(verifier.verify(sig)).isTrue();
    }

    @Test
    void deve_lancar_excecao_para_chave_invalida() {
        assertThatThrownBy(() -> RsaKeyLoader.loadPrivateKey("chave-invalida-!!!"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Failed to load RSA private key");
    }

    // Extrai bytes PKCS#1 de dentro de um PKCS#8 DER.
    // PKCS#8: SEQUENCE { INTEGER 0, AlgorithmIdentifier SEQUENCE, OCTET_STRING { pkcs1 } }
    private static byte[] extractPkcs1FromPkcs8(byte[] pkcs8) {
        int pos = 0;
        pos++;                                   // skip outer SEQUENCE tag (0x30)
        pos += asn1LengthWidth(pkcs8, pos);      // skip outer length
        pos += 3;                                // skip version INTEGER (02 01 00)
        pos++;                                   // skip AlgorithmIdentifier tag (0x30)
        int algoLen = pkcs8[pos] & 0xff;
        pos++;                                   // skip AlgorithmIdentifier length byte
        pos += algoLen;                          // skip AlgorithmIdentifier content
        pos++;                                   // skip OCTET STRING tag (0x04)
        int pkcs1Len = parseAsn1Length(pkcs8, pos);
        pos += asn1LengthWidth(pkcs8, pos);
        return Arrays.copyOfRange(pkcs8, pos, pos + pkcs1Len);
    }

    private static int parseAsn1Length(byte[] data, int pos) {
        int b = data[pos] & 0xff;
        if (b < 128) return b;
        if (b == 0x81) return data[pos + 1] & 0xff;
        return ((data[pos + 1] & 0xff) << 8) | (data[pos + 2] & 0xff);
    }

    private static int asn1LengthWidth(byte[] data, int pos) {
        int b = data[pos] & 0xff;
        if (b < 128) return 1;
        if (b == 0x81) return 2;
        return 3;
    }
}
