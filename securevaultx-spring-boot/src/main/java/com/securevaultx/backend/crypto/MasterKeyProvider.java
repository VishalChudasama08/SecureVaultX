package com.securevaultx.backend.crypto;

import javax.crypto.SecretKey;

/**
 * Source of the key-encryption key ("master key"). Today it is backed by an environment secret;
 * a KMS/HSM-backed implementation can replace it later without touching the business logic.
 */
public interface MasterKeyProvider {

    /** Identifier stored next to every wrapped key so future rotation knows which key to use. */
    String currentKeyId();

    /** @throws CryptoException if this provider does not know the key id */
    SecretKey key(String keyId);
}
