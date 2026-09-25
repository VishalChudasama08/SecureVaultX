package com.securevaultx.backend.crypto;

/** Base type for all cryptography failures. Messages never contain key material. */
public class CryptoException extends RuntimeException {

    public CryptoException(String message) {
        super(message);
    }

    public CryptoException(String message, Throwable cause) {
        super(message, cause);
    }
}
