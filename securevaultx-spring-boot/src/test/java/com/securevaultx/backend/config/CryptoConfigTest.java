package com.securevaultx.backend.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.securevaultx.backend.crypto.MasterKeyProvider;
import org.junit.jupiter.api.Test;

class CryptoConfigTest {

    private final CryptoConfig config = new CryptoConfig();

    @Test
    void startupFailsClearlyWhenMasterKeyIsMissing() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> config.masterKeyProvider(new CryptoProperties("", "k1")));
        assertTrue(e.getMessage().contains("SECUREVAULTX_MASTER_KEY"));
    }

    @Test
    void startupFailsWhenMasterKeyIsMalformed() {
        assertThrows(IllegalStateException.class,
                () -> config.masterKeyProvider(new CryptoProperties("c2hvcnQ=", "k1")));
    }

    @Test
    void validKeyIsAccepted() {
        MasterKeyProvider p = config.masterKeyProvider(
                new CryptoProperties("dGVzdC1tYXN0ZXIta2V5LTAxMjM0NTY3ODlhYmNkZWY=", "k1"));
        assertEquals("k1", p.currentKeyId());
    }

    @Test
    void propertiesToStringNeverContainsTheKey() {
        String key = "dGVzdC1tYXN0ZXIta2V5LTAxMjM0NTY3ODlhYmNkZWY=";
        assertTrue(!new CryptoProperties(key, "k1").toString().contains(key));
    }
}
