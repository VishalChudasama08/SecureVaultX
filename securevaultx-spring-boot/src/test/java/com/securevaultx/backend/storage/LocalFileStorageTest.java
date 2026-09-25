package com.securevaultx.backend.storage;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalFileStorageTest {

    @TempDir
    Path root;

    @Test
    void commitOpenSizeDeleteLifecycle() throws IOException {
        LocalFileStorage storage = new LocalFileStorage(root);
        UUID id = UUID.randomUUID();
        Path tmp = storage.newTempFile();
        Files.write(tmp, new byte[]{1, 2, 3, 4});

        storage.commit(tmp, id);

        assertFalse(Files.exists(tmp), "temp file must be moved, not copied");
        assertTrue(Files.exists(root.resolve("vault").resolve(id + ".enc")));
        assertEquals(4, storage.size(id));
        try (InputStream in = storage.open(id)) {
            assertArrayEquals(new byte[]{1, 2, 3, 4}, in.readAllBytes());
        }
        storage.delete(id);
        storage.delete(id); // idempotent
        assertThrows(StoredFileMissingException.class, () -> storage.open(id));
        assertThrows(StoredFileMissingException.class, () -> storage.size(id));
    }

    @Test
    void storedNamesAreDerivedOnlyFromTheUuid() throws IOException {
        LocalFileStorage storage = new LocalFileStorage(root);
        UUID id = UUID.randomUUID();
        Path tmp = storage.newTempFile();
        Files.write(tmp, new byte[]{9});
        storage.commit(tmp, id);
        try (var files = Files.list(root.resolve("vault"))) {
            assertEquals(id + ".enc", files.findFirst().orElseThrow().getFileName().toString());
        }
    }

    @Test
    void purgeRemovesOnlyStaleTempFiles() throws IOException {
        LocalFileStorage storage = new LocalFileStorage(root);
        Path stale = storage.newTempFile();
        Path fresh = storage.newTempFile();
        Files.setLastModifiedTime(stale, FileTime.from(Instant.now().minus(Duration.ofHours(5))));

        assertEquals(1, storage.purgeStaleTempFiles(Duration.ofHours(1)));
        assertFalse(Files.exists(stale));
        assertTrue(Files.exists(fresh));
    }

    @Test
    void discardTempIsQuietForMissingFiles() throws IOException {
        LocalFileStorage storage = new LocalFileStorage(root);
        Path tmp = storage.newTempFile();
        storage.discardTemp(tmp);
        storage.discardTemp(tmp);
        storage.discardTemp(null);
        assertFalse(Files.exists(tmp));
    }
}
