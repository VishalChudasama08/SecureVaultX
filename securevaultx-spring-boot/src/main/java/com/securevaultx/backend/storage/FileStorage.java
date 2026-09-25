package com.securevaultx.backend.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;

/**
 * Store for encrypted vault files. Files are addressed ONLY by server-generated UUIDs; user-supplied
 * names never reach the filesystem. A cloud object-store implementation could replace the local one.
 */
public interface FileStorage {

    /** Creates an empty private temp file that callers fill before {@link #commit}. */
    Path newTempFile() throws IOException;

    /** Durably and atomically publishes a finished temp file under the given id. */
    void commit(Path tempFile, UUID id);

    /** Best-effort removal of a temp file; never throws. */
    void discardTemp(Path tempFile);

    InputStream open(UUID id);

    long size(UUID id);

    /** Idempotent: succeeds if the file is already gone; throws {@link StorageException} on real I/O failure. */
    void delete(UUID id);

    /** Removes abandoned temp files (crashed uploads); returns how many were removed. */
    int purgeStaleTempFiles(Duration olderThan);
}
