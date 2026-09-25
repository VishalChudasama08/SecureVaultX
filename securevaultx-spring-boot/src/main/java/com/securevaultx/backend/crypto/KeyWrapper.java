package com.securevaultx.backend.crypto;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.UUID;
import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Envelope-encryption step: seals a per-file data key under the master key with AES-256-GCM.
 *
 * <p>Each wrap uses a fresh random 96-bit nonce that is unrelated to the file's chunk nonces. The
 * AAD binds the wrapped key to the record UUID and the format version, so a wrapped key copied onto
 * another record will not unwrap.
 */
public final class KeyWrapper {

    public static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final byte[] AAD_LABEL = "SVX-KEYWRAP".getBytes(StandardCharsets.US_ASCII);

    private final SecureRandom random;

    public KeyWrapper() {
        this(new SecureRandom());
    }

    public KeyWrapper(SecureRandom random) {
        this.random = random;
    }

    /** Generates a new random AES-256 data-encryption key. */
    public SecretKey newDataKey() {
        byte[] raw = new byte[ChunkedFileCipher.KEY_BYTES];
        random.nextBytes(raw);
        SecretKeySpec key = new SecretKeySpec(raw, "AES");
        Arrays.fill(raw, (byte) 0);
        return key;
    }

    public WrappedKey wrap(SecretKey masterKey, SecretKey dataKey, UUID recordId, int formatVersion) {
        byte[] nonce = new byte[NONCE_BYTES];
        random.nextBytes(nonce);
        byte[] raw = dataKey.getEncoded();
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, masterKey, new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(aad(recordId, formatVersion));
            return new WrappedKey(cipher.doFinal(raw), nonce);
        } catch (GeneralSecurityException e) {
            throw new CryptoException("Key wrapping failed", e);
        } finally {
            Arrays.fill(raw, (byte) 0);
        }
    }

    public SecretKey unwrap(SecretKey masterKey, WrappedKey wrapped, UUID recordId, int formatVersion) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, masterKey, new GCMParameterSpec(TAG_BITS, wrapped.nonce()));
            cipher.updateAAD(aad(recordId, formatVersion));
            byte[] raw = cipher.doFinal(wrapped.ciphertext());
            try {
                if (raw.length != ChunkedFileCipher.KEY_BYTES) {
                    throw new CryptoException("Unwrapped key has an unexpected length");
                }
                return new SecretKeySpec(raw, "AES");
            } finally {
                Arrays.fill(raw, (byte) 0);
            }
        } catch (AEADBadTagException e) {
            throw new AuthenticationFailedException(
                    "Could not unwrap data key: wrong master key or tampered record", e);
        } catch (GeneralSecurityException e) {
            throw new CryptoException("Key unwrapping failed", e);
        }
    }

    private static byte[] aad(UUID recordId, int formatVersion) {
        return ByteBuffer.allocate(AAD_LABEL.length + 1 + 16)
                .put(AAD_LABEL)
                .put((byte) formatVersion)
                .putLong(recordId.getMostSignificantBits())
                .putLong(recordId.getLeastSignificantBits())
                .array();
    }
}
