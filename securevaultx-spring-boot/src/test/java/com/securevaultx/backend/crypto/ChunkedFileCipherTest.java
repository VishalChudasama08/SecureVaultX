package com.securevaultx.backend.crypto;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.UUID;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ChunkedFileCipherTest {

    private static final int CHUNK = 64;
    private final SecureRandom random = new SecureRandom();
    private final KeyWrapper keys = new KeyWrapper();
    private final ChunkedFileCipher cipher = new ChunkedFileCipher(random, CHUNK);

    private byte[] randomBytes(int n) {
        byte[] b = new byte[n];
        random.nextBytes(b);
        return b;
    }

    private byte[] encrypt(SecretKey key, UUID id, byte[] plain) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        long n = cipher.encrypt(key, cipher.newHeader(id), new ByteArrayInputStream(plain), out);
        assertEquals(plain.length, n);
        return out.toByteArray();
    }

    private byte[] decrypt(SecretKey key, byte[] enc) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        cipher.decrypt(key, new ByteArrayInputStream(enc), out);
        return out.toByteArray();
    }

    // ---- round trip -------------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 15, 16, CHUNK - 1, CHUNK, CHUNK + 1, 2 * CHUNK - 1, 2 * CHUNK, 2 * CHUNK + 1,
            5 * CHUNK, 5 * CHUNK + 17, 10_000})
    void roundTripReturnsIdenticalBytes(int size) throws IOException {
        SecretKey key = keys.newDataKey();
        byte[] plain = randomBytes(size);
        byte[] enc = encrypt(key, UUID.randomUUID(), plain);

        assertEquals(cipher.encryptedLength(size), enc.length, "encryptedLength() must be exact");
        assertArrayEquals(plain, decrypt(key, enc));
    }

    @Test
    void defaultChunkSizeRoundTrip() throws IOException {
        ChunkedFileCipher def = new ChunkedFileCipher();
        SecretKey key = keys.newDataKey();
        byte[] plain = randomBytes(ChunkedFileCipher.DEFAULT_CHUNK_SIZE * 3 + 123);
        ByteArrayOutputStream enc = new ByteArrayOutputStream();
        def.encrypt(key, def.newHeader(UUID.randomUUID()), new ByteArrayInputStream(plain), enc);
        ByteArrayOutputStream dec = new ByteArrayOutputStream();
        def.decrypt(key, new ByteArrayInputStream(enc.toByteArray()), dec);
        assertArrayEquals(plain, dec.toByteArray());
    }

    @Test
    void sameInputEncryptedTwiceProducesDifferentOutput() throws IOException {
        byte[] plain = randomBytes(500);
        UUID id = UUID.randomUUID();
        // different data keys AND different nonce prefixes
        byte[] a = encrypt(keys.newDataKey(), id, plain);
        byte[] b = encrypt(keys.newDataKey(), id, plain);
        assertFalse(Arrays.equals(a, b));
        // even with the SAME key the random per-file nonce prefix makes the output differ
        SecretKey k = keys.newDataKey();
        assertFalse(Arrays.equals(encrypt(k, id, plain), encrypt(k, id, plain)));
    }

    @Test
    void ciphertextDoesNotContainPlaintext() throws IOException {
        byte[] plain = new byte[200];
        Arrays.fill(plain, (byte) 'A');
        byte[] enc = encrypt(keys.newDataKey(), UUID.randomUUID(), plain);
        String body = new String(enc, EncryptedFileHeader.LENGTH, enc.length - EncryptedFileHeader.LENGTH,
                java.nio.charset.StandardCharsets.ISO_8859_1);
        assertFalse(body.contains("AAAAAAAAAAAAAAAA"));
    }

    // ---- tamper / failure cases ---------------------------------------------------------------

    @Test
    void wrongKeyFails() throws IOException {
        byte[] enc = encrypt(keys.newDataKey(), UUID.randomUUID(), randomBytes(300));
        assertThrows(AuthenticationFailedException.class, () -> decrypt(keys.newDataKey(), enc));
    }

    @Test
    void flippingAnyByteFails() throws IOException {
        SecretKey key = keys.newDataKey();
        byte[] enc = encrypt(key, UUID.randomUUID(), randomBytes(3 * CHUNK + 5));
        // sample positions across header, chunk bodies and tags
        for (int pos = 0; pos < enc.length; pos++) {
            byte[] bad = enc.clone();
            bad[pos] ^= 0x01;
            final int p = pos;
            assertThrows(CryptoException.class, () -> decrypt(key, bad), "byte " + p + " flip must be detected");
        }
    }

    @Test
    void truncationAtChunkBoundaryFails() throws IOException {
        SecretKey key = keys.newDataKey();
        byte[] enc = encrypt(key, UUID.randomUUID(), randomBytes(4 * CHUNK)); // 4 full chunks
        int sealed = CHUNK + ChunkedFileCipher.TAG_BYTES;
        // drop the last chunk entirely: the remaining last chunk was not sealed with the "last" flag
        byte[] cut = Arrays.copyOf(enc, enc.length - sealed);
        assertThrows(AuthenticationFailedException.class, () -> decrypt(key, cut));
    }

    @Test
    void truncationMidChunkFails() throws IOException {
        SecretKey key = keys.newDataKey();
        byte[] enc = encrypt(key, UUID.randomUUID(), randomBytes(3 * CHUNK + 10));
        for (int cut : new int[]{1, 5, 16, 17, 40}) {
            byte[] bad = Arrays.copyOf(enc, enc.length - cut);
            assertThrows(CryptoException.class, () -> decrypt(key, bad));
        }
    }

    @Test
    void truncationToHeaderOnlyOrLessFails() throws IOException {
        SecretKey key = keys.newDataKey();
        byte[] enc = encrypt(key, UUID.randomUUID(), randomBytes(100));
        assertThrows(InvalidEncryptedFileException.class,
                () -> decrypt(key, Arrays.copyOf(enc, EncryptedFileHeader.LENGTH)));
        assertThrows(InvalidEncryptedFileException.class,
                () -> decrypt(key, Arrays.copyOf(enc, EncryptedFileHeader.LENGTH - 1)));
        assertThrows(InvalidEncryptedFileException.class, () -> decrypt(key, new byte[0]));
    }

    @Test
    void appendedDataFails() throws IOException {
        SecretKey key = keys.newDataKey();
        byte[] enc = encrypt(key, UUID.randomUUID(), randomBytes(2 * CHUNK));
        byte[] longer = Arrays.copyOf(enc, enc.length + 40);
        assertThrows(CryptoException.class, () -> decrypt(key, longer));
    }

    @Test
    void swappingChunksFails() throws IOException {
        SecretKey key = keys.newDataKey();
        byte[] enc = encrypt(key, UUID.randomUUID(), randomBytes(4 * CHUNK + 3));
        int sealed = CHUNK + ChunkedFileCipher.TAG_BYTES;
        byte[] bad = enc.clone();
        int h = EncryptedFileHeader.LENGTH;
        System.arraycopy(enc, h, bad, h + sealed, sealed);
        System.arraycopy(enc, h + sealed, bad, h, sealed);
        assertThrows(AuthenticationFailedException.class, () -> decrypt(key, bad));
    }

    @Test
    void chunkFromAnotherFileWithSameKeyFails() throws IOException {
        SecretKey key = keys.newDataKey();
        byte[] a = encrypt(key, UUID.randomUUID(), randomBytes(2 * CHUNK));
        byte[] b = encrypt(key, UUID.randomUUID(), randomBytes(2 * CHUNK));
        int sealed = CHUNK + ChunkedFileCipher.TAG_BYTES;
        byte[] franken = a.clone();
        System.arraycopy(b, EncryptedFileHeader.LENGTH, franken, EncryptedFileHeader.LENGTH, sealed);
        assertThrows(AuthenticationFailedException.class, () -> decrypt(key, franken));
    }

    @Test
    void streamIsNeverReleasedUnauthenticated() throws IOException {
        // Corrupt only the 3rd chunk: chunks 1-2 may be released (each was individually authenticated),
        // but the corrupted chunk's plaintext must never reach the output.
        SecretKey key = keys.newDataKey();
        byte[] plain = randomBytes(4 * CHUNK);
        byte[] enc = encrypt(key, UUID.randomUUID(), plain);
        int sealed = CHUNK + ChunkedFileCipher.TAG_BYTES;
        enc[EncryptedFileHeader.LENGTH + 2 * sealed + 3] ^= 0x40;

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertThrows(AuthenticationFailedException.class,
                () -> cipher.decrypt(key, new ByteArrayInputStream(enc), out));
        assertEquals(2 * CHUNK, out.size());
        assertArrayEquals(Arrays.copyOf(plain, 2 * CHUNK), out.toByteArray());
    }

    // ---- header handling ------------------------------------------------------------------------

    @Test
    void badMagicIsRejected() throws IOException {
        byte[] enc = encrypt(keys.newDataKey(), UUID.randomUUID(), randomBytes(10));
        enc[0] = 'X';
        assertThrows(InvalidEncryptedFileException.class, () -> EncryptedFileHeader.read(new ByteArrayInputStream(enc)));
    }

    @Test
    void unsupportedVersionIsReportedDistinctly() throws IOException {
        byte[] enc = encrypt(keys.newDataKey(), UUID.randomUUID(), randomBytes(10));
        enc[4] = 2;
        UnsupportedFormatVersionException e = assertThrows(UnsupportedFormatVersionException.class,
                () -> EncryptedFileHeader.read(new ByteArrayInputStream(enc)));
        assertEquals(2, e.version());
    }

    @Test
    void hostileChunkSizeInHeaderIsRejected() throws IOException {
        byte[] enc = encrypt(keys.newDataKey(), UUID.randomUUID(), randomBytes(10));
        enc[6] = 0x7F; // chunk size ~2 GB
        assertThrows(InvalidEncryptedFileException.class, () -> EncryptedFileHeader.read(new ByteArrayInputStream(enc)));
        enc[6] = 0; enc[7] = 0; enc[8] = 0; enc[9] = 0; // zero
        assertThrows(InvalidEncryptedFileException.class, () -> EncryptedFileHeader.read(new ByteArrayInputStream(enc)));
    }

    @Test
    void headerRoundTripsAndCarriesRecordId() throws IOException {
        UUID id = UUID.randomUUID();
        EncryptedFileHeader h = cipher.newHeader(id);
        EncryptedFileHeader back = EncryptedFileHeader.read(new ByteArrayInputStream(h.toBytes()));
        assertEquals(id, back.recordId());
        assertEquals(CHUNK, back.chunkSize());
        assertArrayEquals(h.noncePrefix(), back.noncePrefix());
        assertEquals(EncryptedFileHeader.LENGTH, h.toBytes().length);
    }

    @Test
    void nonAes256KeyIsRejected() {
        SecretKey weak = new SecretKeySpec(new byte[16], "AES");
        assertThrows(IllegalArgumentException.class, () -> cipher.encrypt(weak,
                cipher.newHeader(UUID.randomUUID()), new ByteArrayInputStream(new byte[1]), OutputStream.nullOutputStream()));
    }

    // ---- large file, constant memory ----------------------------------------------------------------

    /** Deterministic pseudo-random stream that never materialises the data in memory. */
    private static final class PatternInputStream extends InputStream {
        private long remaining;
        private long state = 0x9E3779B97F4A7C15L;

        PatternInputStream(long size) {
            this.remaining = size;
        }

        @Override
        public int read() {
            if (remaining <= 0) {
                return -1;
            }
            remaining--;
            state = state * 6364136223846793005L + 1442695040888963407L;
            return (int) (state >>> 56) & 0xFF;
        }

        @Override
        public int read(byte[] b, int off, int len) {
            if (remaining <= 0) {
                return -1;
            }
            int n = (int) Math.min(len, remaining);
            for (int i = 0; i < n; i++) {
                b[off + i] = (byte) read();
            }
            return n;
        }
    }

    private static final class DigestOutputStream extends OutputStream {
        final MessageDigest md;
        long count;

        DigestOutputStream(MessageDigest md) {
            this.md = md;
        }

        @Override
        public void write(int b) {
            md.update((byte) b);
            count++;
        }

        @Override
        public void write(byte[] b, int off, int len) {
            md.update(b, off, len);
            count += len;
        }
    }

    @Test
    void largeStreamedFileRoundTrip() throws Exception {
        final long size = 48L * 1024 * 1024 + 12_345; // not a multiple of the chunk size
        ChunkedFileCipher def = new ChunkedFileCipher();
        SecretKey key = keys.newDataKey();

        MessageDigest expected = MessageDigest.getInstance("SHA-256");
        try (InputStream in = new PatternInputStream(size)) {
            in.transferTo(new DigestOutputStream(expected));
        }

        java.nio.file.Path tmp = java.nio.file.Files.createTempFile("svx-large", ".enc");
        try {
            try (OutputStream out = new java.io.BufferedOutputStream(java.nio.file.Files.newOutputStream(tmp))) {
                def.encrypt(key, def.newHeader(UUID.randomUUID()), new PatternInputStream(size), out);
            }
            assertEquals(def.encryptedLength(size), java.nio.file.Files.size(tmp));

            DigestOutputStream actual = new DigestOutputStream(MessageDigest.getInstance("SHA-256"));
            try (InputStream in = new java.io.BufferedInputStream(java.nio.file.Files.newInputStream(tmp))) {
                def.decrypt(key, in, actual);
            }
            assertEquals(size, actual.count);
            assertArrayEquals(expected.digest(), actual.md.digest());
        } finally {
            java.nio.file.Files.deleteIfExists(tmp);
        }
    }
}
