package com.securevaultx.backend.crypto;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;

class KeyWrapperTest {

    private final KeyWrapper wrapper = new KeyWrapper();

    @Test
    void wrapThenUnwrapRestoresTheKey() {
        SecretKey master = wrapper.newDataKey(); // any random AES-256 key works as a master key here
        SecretKey dek = wrapper.newDataKey();
        UUID id = UUID.randomUUID();

        WrappedKey w = wrapper.wrap(master, dek, id, 1);
        assertEquals(32 + 16, w.ciphertext().length, "32-byte key + 16-byte GCM tag");
        assertEquals(KeyWrapper.NONCE_BYTES, w.nonce().length);
        assertFalse(Arrays.equals(dek.getEncoded(), Arrays.copyOf(w.ciphertext(), 32)));
        assertArrayEquals(dek.getEncoded(), wrapper.unwrap(master, w, id, 1).getEncoded());
    }

    @Test
    void everyWrapUsesAFreshNonceAndDataKeysAreUnique() {
        SecretKey master = wrapper.newDataKey();
        SecretKey dek = wrapper.newDataKey();
        UUID id = UUID.randomUUID();
        Set<String> nonces = new HashSet<>();
        Set<String> keys = new HashSet<>();
        for (int i = 0; i < 2000; i++) {
            nonces.add(java.util.HexFormat.of().formatHex(wrapper.wrap(master, dek, id, 1).nonce()));
            keys.add(java.util.HexFormat.of().formatHex(wrapper.newDataKey().getEncoded()));
        }
        assertEquals(2000, nonces.size());
        assertEquals(2000, keys.size());
    }

    @Test
    void wrongMasterKeyFails() {
        SecretKey dek = wrapper.newDataKey();
        UUID id = UUID.randomUUID();
        WrappedKey w = wrapper.wrap(wrapper.newDataKey(), dek, id, 1);
        assertThrows(AuthenticationFailedException.class, () -> wrapper.unwrap(wrapper.newDataKey(), w, id, 1));
    }

    @Test
    void wrappedKeyCannotBeMovedToAnotherRecord() {
        SecretKey master = wrapper.newDataKey();
        WrappedKey w = wrapper.wrap(master, wrapper.newDataKey(), UUID.randomUUID(), 1);
        assertThrows(AuthenticationFailedException.class,
                () -> wrapper.unwrap(master, w, UUID.randomUUID(), 1));
    }

    @Test
    void versionIsBoundIntoTheWrap() {
        SecretKey master = wrapper.newDataKey();
        UUID id = UUID.randomUUID();
        WrappedKey w = wrapper.wrap(master, wrapper.newDataKey(), id, 1);
        assertThrows(AuthenticationFailedException.class, () -> wrapper.unwrap(master, w, id, 2));
    }

    @Test
    void tamperedCiphertextOrNonceFails() {
        SecretKey master = wrapper.newDataKey();
        UUID id = UUID.randomUUID();
        WrappedKey w = wrapper.wrap(master, wrapper.newDataKey(), id, 1);

        byte[] ct = w.ciphertext();
        ct[0] ^= 1;
        assertThrows(AuthenticationFailedException.class,
                () -> wrapper.unwrap(master, new WrappedKey(ct, w.nonce()), id, 1));

        byte[] nonce = w.nonce();
        nonce[0] ^= 1;
        assertThrows(AuthenticationFailedException.class,
                () -> wrapper.unwrap(master, new WrappedKey(w.ciphertext(), nonce), id, 1));
    }

    @Test
    void wrapDoesNotAliasCallerKeyMaterial() {
        SecretKey dek = wrapper.newDataKey();
        byte[] before = dek.getEncoded();
        wrapper.wrap(wrapper.newDataKey(), dek, UUID.randomUUID(), 1);
        assertArrayEquals(before, dek.getEncoded());
        assertNotEquals(0, dek.getEncoded()[0] | dek.getEncoded()[1] | dek.getEncoded()[2] | dek.getEncoded()[3]);
    }
}
