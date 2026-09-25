package com.securevaultx.backend.crypto;

/**
 * AES-GCM authentication failed: the data was modified, truncated, reordered, extended,
 * or the wrong key was used. Plaintext produced for the failing chunk is never released.
 */
public class AuthenticationFailedException extends CryptoException {

    public AuthenticationFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
