package com.securevaultx.backend.crypto;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.UUID;
import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Streaming authenticated encryption for arbitrarily large files, built only from the standard
 * JCA primitive AES-256-GCM (no custom cipher).
 *
 * <p>Design ("STREAM" construction, as used by Tink/age-style formats):
 * <ul>
 *   <li>The plaintext is cut into fixed-size chunks; each chunk is sealed by its own AES-GCM call,
 *       so memory use is constant (about two chunks) regardless of file size.</li>
 *   <li>Nonce (12 bytes) = 7-byte random per-file prefix || 4-byte chunk counter || 1-byte "last chunk"
 *       flag. Within one file the counter makes every nonce unique; across files the per-file random
 *       data key makes reuse harmless.</li>
 *   <li>AAD of every chunk = whole file header || 8-byte chunk index || last-chunk flag. This binds each
 *       chunk to this file (record UUID, version, chunk size), to its position and to the end marker.</li>
 *   <li>Truncation (dropping the tail), reordering, duplication and appending are all detected,
 *       because the final chunk is the only one sealed with the "last" flag.</li>
 * </ul>
 *
 * <p>Instances are immutable and thread-safe; all per-operation state lives on the stack.
 */
public final class ChunkedFileCipher {

    public static final int TAG_BYTES = 16;
    public static final int KEY_BYTES = 32;
    public static final int NONCE_BYTES = 12;
    public static final int DEFAULT_CHUNK_SIZE = 64 * 1024;

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";

    private final SecureRandom random;
    private final int chunkSize;

    public ChunkedFileCipher() {
        this(new SecureRandom(), DEFAULT_CHUNK_SIZE);
    }

    public ChunkedFileCipher(SecureRandom random, int chunkSize) {
        if (chunkSize < 1 || chunkSize > EncryptedFileHeader.MAX_CHUNK_SIZE) {
            throw new IllegalArgumentException("chunkSize out of range");
        }
        this.random = random;
        this.chunkSize = chunkSize;
    }

    /** Creates a header with a fresh random nonce prefix for the given encryption record. */
    public EncryptedFileHeader newHeader(UUID recordId) {
        byte[] prefix = new byte[EncryptedFileHeader.NONCE_PREFIX_LENGTH];
        random.nextBytes(prefix);
        return new EncryptedFileHeader(EncryptedFileHeader.CURRENT_VERSION, chunkSize, recordId, prefix);
    }

    /** Exact size of the encrypted file for a given plaintext length (used for Content-Length). */
    public static long encryptedLength(long plaintextLength, int chunkSize) {
        long chunks = Math.max(1L, (plaintextLength + chunkSize - 1) / chunkSize);
        return EncryptedFileHeader.LENGTH + plaintextLength + chunks * TAG_BYTES;
    }

    public long encryptedLength(long plaintextLength) {
        return encryptedLength(plaintextLength, chunkSize);
    }

    /**
     * Writes the header followed by the sealed chunks. Does not close either stream.
     *
     * @return number of plaintext bytes consumed
     */
    public long encrypt(SecretKey key, EncryptedFileHeader header, InputStream in, OutputStream out)
            throws IOException {
        requireAes256(key);
        final int size = header.chunkSize();
        final byte[] headerBytes = header.toBytes();
        out.write(headerBytes);

        Cipher cipher = newCipher();
        byte[] cur = new byte[size];
        byte[] next = new byte[size];
        byte[] sealed = new byte[size + TAG_BYTES];

        int curLen = readFully(in, cur);
        long index = 0;
        long total = 0;
        while (true) {
            // One-chunk look-ahead: a chunk is "last" only if nothing follows it.
            int nextLen = (curLen == size) ? readFully(in, next) : 0;
            boolean last = nextLen == 0;

            init(cipher, Cipher.ENCRYPT_MODE, key, header, index, last, headerBytes);
            try {
                int n = cipher.doFinal(cur, 0, curLen, sealed, 0);
                out.write(sealed, 0, n);
            } catch (GeneralSecurityException e) {
                throw new CryptoException("Encryption failed", e);
            }
            total += curLen;
            if (last) {
                return total;
            }
            byte[] tmp = cur;
            cur = next;
            next = tmp;
            curLen = nextLen;
            index++;
        }
    }

    /** Reads the header itself, then authenticates and decrypts the body. Does not close streams. */
    public long decrypt(SecretKey key, InputStream in, OutputStream out) throws IOException {
        return decryptBody(key, EncryptedFileHeader.read(in), in, out);
    }

    /**
     * Decrypts a body whose header has already been read from {@code in}.
     * Every chunk is authenticated before its plaintext is written. The caller must treat the
     * operation as successful only if this method returns normally: an exception (also for
     * truncated input) means the output is incomplete or untrusted.
     */
    public long decryptBody(SecretKey key, EncryptedFileHeader header, InputStream in, OutputStream out)
            throws IOException {
        requireAes256(key);
        final int sealedSize = header.chunkSize() + TAG_BYTES;
        final byte[] headerBytes = header.toBytes();

        Cipher cipher = newCipher();
        byte[] cur = new byte[sealedSize];
        byte[] next = new byte[sealedSize];
        byte[] plain = new byte[header.chunkSize()];

        int curLen = readFully(in, cur);
        if (curLen < TAG_BYTES) {
            throw new InvalidEncryptedFileException("Encrypted file is truncated");
        }
        long index = 0;
        long total = 0;
        while (true) {
            int nextLen = (curLen == sealedSize) ? readFully(in, next) : 0;
            boolean last = nextLen == 0;
            if (!last && nextLen < TAG_BYTES) {
                throw new InvalidEncryptedFileException("Encrypted file is truncated");
            }

            init(cipher, Cipher.DECRYPT_MODE, key, header, index, last, headerBytes);
            try {
                int n = cipher.doFinal(cur, 0, curLen, plain, 0);
                out.write(plain, 0, n);
                total += n;
            } catch (AEADBadTagException e) {
                throw new AuthenticationFailedException(
                        "Authentication failed: file is corrupted, modified, or the key is wrong", e);
            } catch (GeneralSecurityException e) {
                throw new CryptoException("Decryption failed", e);
            }
            if (last) {
                return total;
            }
            byte[] tmp = cur;
            cur = next;
            next = tmp;
            curLen = nextLen;
            index++;
        }
    }

    private static Cipher newCipher() {
        try {
            return Cipher.getInstance(TRANSFORMATION);
        } catch (GeneralSecurityException e) {
            throw new CryptoException("AES/GCM is not available on this JVM", e);
        }
    }

    private static void init(Cipher cipher, int mode, SecretKey key, EncryptedFileHeader header,
                             long index, boolean last, byte[] headerBytes) {
        if (index > 0xFFFFFFFFL) {
            throw new CryptoException("File too large for this format version");
        }
        byte[] nonce = ByteBuffer.allocate(NONCE_BYTES)
                .put(header.noncePrefix())
                .putInt((int) index)
                .put((byte) (last ? 1 : 0))
                .array();
        byte[] aad = ByteBuffer.allocate(headerBytes.length + 8 + 1)
                .put(headerBytes)
                .putLong(index)
                .put((byte) (last ? 1 : 0))
                .array();
        try {
            cipher.init(mode, key, new GCMParameterSpec(TAG_BYTES * 8, nonce));
            cipher.updateAAD(aad);
        } catch (GeneralSecurityException e) {
            throw new CryptoException("Cipher initialisation failed", e);
        }
    }

    private static void requireAes256(SecretKey key) {
        byte[] enc = key.getEncoded();
        boolean ok = "AES".equalsIgnoreCase(key.getAlgorithm()) && enc != null && enc.length == KEY_BYTES;
        if (enc != null) {
            java.util.Arrays.fill(enc, (byte) 0);
        }
        if (!ok) {
            throw new IllegalArgumentException("An AES-256 key is required");
        }
    }

    /** Reads until the buffer is full or EOF; returns the number of bytes read (possibly 0). */
    private static int readFully(InputStream in, byte[] buf) throws IOException {
        int off = 0;
        while (off < buf.length) {
            int r = in.read(buf, off, buf.length - off);
            if (r < 0) {
                break;
            }
            off += r;
        }
        return off;
    }
}
