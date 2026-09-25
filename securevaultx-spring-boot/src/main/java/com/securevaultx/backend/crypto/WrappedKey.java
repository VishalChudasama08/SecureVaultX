package com.securevaultx.backend.crypto;

/** A data-encryption key sealed under the master key: AES-GCM ciphertext (key + tag) and its nonce. */
public record WrappedKey(byte[] ciphertext, byte[] nonce) {

    public WrappedKey {
        ciphertext = ciphertext.clone();
        nonce = nonce.clone();
    }

    @Override
    public byte[] ciphertext() {
        return ciphertext.clone();
    }

    @Override
    public byte[] nonce() {
        return nonce.clone();
    }
}
