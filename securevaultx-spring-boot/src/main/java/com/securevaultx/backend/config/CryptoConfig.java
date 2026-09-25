package com.securevaultx.backend.config;

import com.securevaultx.backend.crypto.ChunkedFileCipher;
import com.securevaultx.backend.crypto.EnvironmentMasterKeyProvider;
import com.securevaultx.backend.crypto.KeyWrapper;
import com.securevaultx.backend.crypto.MasterKeyProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires the framework-free crypto classes into Spring. */
@Configuration
public class CryptoConfig {

    /**
     * Fails application start-up (with an explanatory message) if the master key is missing or malformed.
     * A new key is NEVER generated silently: that would make every existing file undecryptable.
     */
    @Bean
    MasterKeyProvider masterKeyProvider(CryptoProperties props) {
        try {
            return new EnvironmentMasterKeyProvider(props.keyId(), props.masterKey());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Invalid SecureVaultX master key configuration: " + e.getMessage(), e);
        }
    }

    @Bean
    ChunkedFileCipher chunkedFileCipher() {
        return new ChunkedFileCipher();
    }

    @Bean
    KeyWrapper keyWrapper() {
        return new KeyWrapper();
    }
}
