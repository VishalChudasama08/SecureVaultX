package com.securevaultx.backend.crypto;

/** The bytes are not a well-formed SecureVaultX encrypted file (bad magic, truncated, bad header...). */
public class InvalidEncryptedFileException extends CryptoException {

    public InvalidEncryptedFileException(String message) {
        super(message);
    }
}
