package com.securevaultx.backend.response;

import com.securevaultx.backend.entities.EncryptionRecord;
import java.time.Instant;
import java.util.UUID;

public record VaultFileResponse(UUID id, String filename, long size, Instant createdAt) {

    public static VaultFileResponse from(EncryptionRecord r) {
        return new VaultFileResponse(r.getPublicId(), r.getOriginalFilename(), r.getOriginalSize(), r.getCreatedAt());
    }
}
