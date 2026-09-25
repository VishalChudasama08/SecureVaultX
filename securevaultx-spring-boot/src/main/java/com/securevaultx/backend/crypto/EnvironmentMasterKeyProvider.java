package com.securevaultx.backend.crypto;

import javax.crypto.SecretKey;

/**
 * Master key supplied through configuration (environment variable SECUREVAULTX_MASTER_KEY).
 * Construction fails fast if the key is missing or malformed, so the application never starts
 * with a broken or silently generated key.
 */
public final class EnvironmentMasterKeyProvider implements MasterKeyProvider {

    private final String keyId;
    private final SecretKey key;

    public EnvironmentMasterKeyProvider(String keyId, String encodedKey) {
        if (keyId == null || keyId.isBlank() || keyId.length() > 32) {
            throw new IllegalArgumentException("Master key id must be 1-32 characters");
        }
        this.keyId = keyId;
        this.key = MasterKeyParser.parse(encodedKey);
    }

    @Override
    public String currentKeyId() {
        return keyId;
    }

    @Override
    public SecretKey key(String requestedKeyId) {
        if (!keyId.equals(requestedKeyId)) {
            throw new CryptoException("Unknown master key id: " + requestedKeyId);
        }
        return key;
    }

    @Override
    public String toString() {
        return "EnvironmentMasterKeyProvider[keyId=" + keyId + "]"; // never print key material
    }
}
