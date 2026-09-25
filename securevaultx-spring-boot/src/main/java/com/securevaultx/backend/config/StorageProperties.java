package com.securevaultx.backend.config;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;

/**
 * @param root              directory that holds encrypted vault files (never served statically)
 * @param maxVaultFileSize  product policy: biggest file that may be stored in the server vault
 * @param maxUploadSize     operational limit for any single upload (Encrypt &amp; Download / decrypt)
 */
@ConfigurationProperties(prefix = "securevaultx.storage")
public record StorageProperties(
        @DefaultValue("./data") Path root,
        @DefaultValue("5MB") DataSize maxVaultFileSize,
        @DefaultValue("2GB") DataSize maxUploadSize) {
}
