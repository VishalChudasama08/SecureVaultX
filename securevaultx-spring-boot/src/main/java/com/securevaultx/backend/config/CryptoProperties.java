package com.securevaultx.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Never log or serialise this object: it carries the master key. */
@ConfigurationProperties(prefix = "securevaultx.crypto")
public record CryptoProperties(
        @DefaultValue("") String masterKey,
        @DefaultValue("k1") String keyId) {

    @Override
    public String toString() {
        return "CryptoProperties[keyId=" + keyId + ", masterKey=***]";
    }
}
