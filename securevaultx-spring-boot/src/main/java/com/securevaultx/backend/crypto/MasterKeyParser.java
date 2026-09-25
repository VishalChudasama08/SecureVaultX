package com.securevaultx.backend.crypto;

import java.util.Arrays;
import java.util.Base64;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

/** Validates and decodes the configured master key. Error messages never echo the secret. */
public final class MasterKeyParser {

    private MasterKeyParser() {
    }

    /**
     * @param encoded base64 (standard or URL-safe) encoding of exactly 32 random bytes
     * @throws IllegalArgumentException if missing or malformed
     */
    public static SecretKey parse(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            throw new IllegalArgumentException(
                    "Master key is not configured. Set SECUREVAULTX_MASTER_KEY to a base64-encoded "
                            + "random 256-bit value (e.g. `openssl rand -base64 32`).");
        }
        String trimmed = encoded.trim();
        byte[] raw;
        try {
            raw = Base64.getDecoder().decode(trimmed);
        } catch (IllegalArgumentException standardFailed) {
            try {
                raw = Base64.getUrlDecoder().decode(trimmed);
            } catch (IllegalArgumentException urlFailed) {
                throw new IllegalArgumentException(
                        "Master key is not valid base64. Expected base64 of exactly 32 random bytes.");
            }
        }
        try {
            if (raw.length != ChunkedFileCipher.KEY_BYTES) {
                throw new IllegalArgumentException(
                        "Master key must decode to exactly 32 bytes (256 bits) but decoded to "
                                + raw.length + " bytes.");
            }
            if (isAllSame(raw)) {
                throw new IllegalArgumentException(
                        "Master key looks like a placeholder (all bytes identical). Generate a random one.");
            }
            return new SecretKeySpec(raw, "AES");
        } finally {
            Arrays.fill(raw, (byte) 0);
        }
    }

    private static boolean isAllSame(byte[] raw) {
        for (byte b : raw) {
            if (b != raw[0]) {
                return false;
            }
        }
        return true;
    }
}
