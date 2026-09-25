package com.securevaultx.backend.crypto;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.UUID;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

/**
 * Protects the on-disk contract of encrypted-file format version 1. If either test fails, a refactor
 * changed the byte layout / nonce / AAD scheme and would make previously generated .enc files unreadable.
 * Bump the format version instead of changing these vectors.
 */
class FormatContractTest {

    private static final SecretKey KEY = new SecretKeySpec(
            HexFormat.of().parseHex("000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f"), "AES");
    private static final UUID ID = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final EncryptedFileHeader HEADER = new EncryptedFileHeader(
            1, 16, ID, HexFormat.of().parseHex("a1a2a3a4a5a6a7"));
    private static final byte[] PLAIN = "SecureVaultX-format-v1-test-vector!".getBytes(StandardCharsets.US_ASCII);

    /** Deterministic output of the v1 format for the fixed inputs above. */
    static final String GOLDEN_HEX = "5356584601000000001011111111222233334444555555555555a1a2a3a4a5a6a7bb675c31717d18926cd375de872b0ecd2207c98ac4461397eaee6eb42b2cdcc2c0ec41545b1d46be606421c5ad8d219217ecc893c2187c49daadfeffa1f5374cfd991f9e46ddcda8f1609a11b70cc32ff813e9";

    @Test
    void version1HeaderLayoutIsStable() {
        assertEquals("53565846" + "01" + "00" + "00000010" + "11111111222233334444555555555555" + "a1a2a3a4a5a6a7",
                HexFormat.of().formatHex(HEADER.toBytes()));
    }

    @Test
    void version1EncryptedBytesAreStable() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new ChunkedFileCipher(new java.security.SecureRandom(), 16)
                .encrypt(KEY, HEADER, new ByteArrayInputStream(PLAIN), out);
        assertEquals(GOLDEN_HEX, HexFormat.of().formatHex(out.toByteArray()));
    }

    @Test
    void goldenFileStillDecrypts() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new ChunkedFileCipher().decrypt(KEY, new ByteArrayInputStream(HexFormat.of().parseHex(GOLDEN_HEX)), out);
        assertArrayEquals(PLAIN, out.toByteArray());
    }
}
