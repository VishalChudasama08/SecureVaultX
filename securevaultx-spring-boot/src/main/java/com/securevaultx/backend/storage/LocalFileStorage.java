package com.securevaultx.backend.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Local-disk implementation. Layout under the configured root:
 * <pre>
 *   vault/&lt;uuid&gt;.enc     committed encrypted files
 *   tmp/upload-*.part    in-progress writes (atomically moved into vault/ on success)
 * </pre>
 * The root must never be exposed as a static resource; all access goes through authenticated controllers.
 */
public class LocalFileStorage implements FileStorage {

    private static final String EXTENSION = ".enc";

    private final Path vaultDir;
    private final Path tmpDir;

    public LocalFileStorage(Path root) {
        Path absolute = root.toAbsolutePath().normalize();
        this.vaultDir = absolute.resolve("vault");
        this.tmpDir = absolute.resolve("tmp");
        try {
            Files.createDirectories(vaultDir);
            Files.createDirectories(tmpDir);
        } catch (IOException e) {
            throw new StorageException("Cannot initialise storage directories", e);
        }
    }

    @Override
    public Path newTempFile() throws IOException {
        // On POSIX systems createTempFile creates the file with owner-only permissions.
        return Files.createTempFile(tmpDir, "upload-", ".part");
    }

    @Override
    public void commit(Path tempFile, UUID id) {
        Path target = target(id);
        try {
            try (FileChannel ch = FileChannel.open(tempFile, StandardOpenOption.WRITE)) {
                ch.force(true); // flush to disk before the file becomes visible
            }
            Files.move(tempFile, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new StorageException("Could not commit encrypted file", e);
        }
    }

    @Override
    public void discardTemp(Path tempFile) {
        if (tempFile == null) {
            return;
        }
        try {
            Files.deleteIfExists(tempFile);
        } catch (IOException ignored) {
            // best effort; the periodic purge will collect it
        }
    }

    @Override
    public InputStream open(UUID id) {
        try {
            return Files.newInputStream(target(id));
        } catch (NoSuchFileException e) {
            throw new StoredFileMissingException("Encrypted file is missing from storage");
        } catch (IOException e) {
            throw new StorageException("Could not open encrypted file", e);
        }
    }

    @Override
    public long size(UUID id) {
        try {
            return Files.size(target(id));
        } catch (NoSuchFileException e) {
            throw new StoredFileMissingException("Encrypted file is missing from storage");
        } catch (IOException e) {
            throw new StorageException("Could not read encrypted file size", e);
        }
    }

    @Override
    public void delete(UUID id) {
        try {
            Files.deleteIfExists(target(id));
        } catch (IOException e) {
            throw new StorageException("Could not delete encrypted file", e);
        }
    }

    @Override
    public int purgeStaleTempFiles(Duration olderThan) {
        Instant cutoff = Instant.now().minus(olderThan);
        int removed = 0;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(tmpDir, "*.part")) {
            for (Path p : stream) {
                try {
                    if (Files.getLastModifiedTime(p).toInstant().isBefore(cutoff) && Files.deleteIfExists(p)) {
                        removed++;
                    }
                } catch (IOException ignored) {
                    // skip files we cannot inspect; next run retries
                }
            }
        } catch (IOException e) {
            throw new StorageException("Could not scan temp directory", e);
        }
        return removed;
    }

    private Path target(UUID id) {
        // A UUID renders as hex digits and dashes only, so traversal is impossible; verify anyway.
        Path p = vaultDir.resolve(id.toString() + EXTENSION).normalize();
        if (!vaultDir.equals(p.getParent())) {
            throw new StorageException("Invalid storage location");
        }
        return p;
    }
}
