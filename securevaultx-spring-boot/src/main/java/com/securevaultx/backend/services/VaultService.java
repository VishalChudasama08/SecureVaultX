package com.securevaultx.backend.services;

import com.securevaultx.backend.config.StorageProperties;
import com.securevaultx.backend.crypto.CryptoException;
import com.securevaultx.backend.crypto.EncryptedFileHeader;
import com.securevaultx.backend.entities.EncryptionRecord;
import com.securevaultx.backend.entities.StorageMode;
import com.securevaultx.backend.exception.ApiException;
import com.securevaultx.backend.repositories.EncryptionRecordRepository;
import com.securevaultx.backend.repositories.UserRepository;
import com.securevaultx.backend.response.VaultFileResponse;
import com.securevaultx.backend.storage.FileStorage;
import com.securevaultx.backend.util.FilenameSanitizer;
import com.securevaultx.backend.util.LimitedInputStream;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Mode A: "Store in Secure Vault". Every method takes the owner id from the authenticated principal and every
 * lookup goes through {@code findByPublicIdAndOwner_Id}, so another user's records simply do not exist here.
 *
 * <p>Filesystem operations cannot be rolled back by a database transaction, so instead of @Transactional this
 * class orders its steps so that any failure leaves either nothing behind or something that can be retried
 * (see the comments in {@link #store} and {@link #delete}).
 */
@Service
public class VaultService {

    private static final Logger log = LoggerFactory.getLogger(VaultService.class);

    private final EncryptionRecordRepository records;
    private final UserRepository users;
    private final FileStorage storage;
    private final EnvelopeCryptoService crypto;
    private final StorageProperties props;

    public VaultService(EncryptionRecordRepository records, UserRepository users, FileStorage storage,
                        EnvelopeCryptoService crypto, StorageProperties props) {
        this.records = records;
        this.users = users;
        this.storage = storage;
        this.crypto = crypto;
        this.props = props;
    }

    /**
     * Encrypts the request body straight into a temp file (no plaintext ever touches the disk), publishes it
     * atomically, then records it in the database. The size policy is enforced on the bytes actually received.
     */
    public VaultFileResponse store(long ownerId, String rawFilename, InputStream body, long declaredLength) {
        final long max = props.maxVaultFileSize().toBytes();
        if (declaredLength > max) {
            throw ApiException.vaultTooLarge(max); // reject early, before reading a single byte
        }
        UUID id = UUID.randomUUID();
        EnvelopeCryptoService.Prepared prepared = crypto.prepare(id);

        Path temp = null;
        try {
            temp = storage.newTempFile();
            long plainSize;
            try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(temp));
                 InputStream in = new LimitedInputStream(body, max, () -> ApiException.vaultTooLarge(max))) {
                plainSize = crypto.encrypt(prepared, in, out);
            }
            // 1) file first: an unreferenced file is harmless and cleaned up below if step 2 fails
            storage.commit(temp, id);
            temp = null;
            // 2) then the database record; on failure remove the file so no orphan is left behind
            try {
                EncryptionRecord saved = records.save(new EncryptionRecord(id, users.getReferenceById(ownerId),
                        FilenameSanitizer.sanitize(rawFilename), plainSize, StorageMode.VAULT,
                        EncryptedFileHeader.CURRENT_VERSION, prepared.masterKeyId(),
                        prepared.wrappedKey().ciphertext(), prepared.wrappedKey().nonce()));
                log.info("Vault file stored (record={}, bytes={})", id, plainSize);
                return VaultFileResponse.from(saved);
            } catch (RuntimeException dbFailure) {
                compensateDelete(id);
                throw dbFailure;
            }
        } catch (IOException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "STORAGE_ERROR",
                    "The file could not be stored.", e);
        } finally {
            storage.discardTemp(temp); // no-op after a successful commit
        }
    }

    public List<VaultFileResponse> list(long ownerId) {
        return records.findByOwner_IdAndStorageModeOrderByCreatedAtDesc(ownerId, StorageMode.VAULT).stream()
                .map(VaultFileResponse::from).toList();
    }

    public VaultFileResponse get(long ownerId, UUID id) {
        return VaultFileResponse.from(findVaultRecord(ownerId, id));
    }

    /** The stored .enc exactly as it is on disk (still encrypted). */
    public StreamedDownload encryptedDownload(long ownerId, UUID id) {
        EncryptionRecord record = findVaultRecord(ownerId, id);
        long size = storage.size(id);
        return StreamedDownload.of(record.getOriginalFilename() + ".enc", size, out -> {
            try (InputStream in = storage.open(id)) {
                in.transferTo(out);
            }
        });
    }

    /**
     * Decrypted content. The whole ciphertext is authenticated in a first pass before the second pass releases
     * any plaintext, so a tampered file never yields a partial "successful" download.
     */
    public StreamedDownload decryptedDownload(long ownerId, UUID id) {
        EncryptionRecord record = findVaultRecord(ownerId, id);
        SecretKey key = crypto.unwrap(record);
        try (InputStream in = new BufferedInputStream(storage.open(id))) {
            EncryptedFileHeader header = readAndCheckHeader(in, record);
            long n = crypto.decryptBody(key, header, in, OutputStream.nullOutputStream());
            if (n != record.getOriginalSize()) {
                throw new CryptoException("Decrypted size does not match the record");
            }
        } catch (CryptoException e) {
            log.error("Integrity check failed for vault record {}: {}", id, e.getMessage());
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "STORED_FILE_CORRUPT",
                    "The stored file failed its integrity check and cannot be decrypted.", e);
        } catch (IOException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "STORAGE_ERROR",
                    "The stored file could not be read.", e);
        }
        return StreamedDownload.of(record.getOriginalFilename(), record.getOriginalSize(), out -> {
            try (InputStream in = new BufferedInputStream(storage.open(id))) {
                EncryptedFileHeader header = readAndCheckHeader(in, record);
                crypto.decryptBody(key, header, in, out);
            }
        });
    }

    /**
     * Removes the encrypted file first, then the record. If the disk delete fails nothing changes and the client
     * gets an error (retry is safe). If only the record delete fails afterwards, a retry succeeds because file
     * deletion is idempotent. Never the reverse order, which could leave an invisible, undeletable orphan file.
     */
    public void delete(long ownerId, UUID id) {
        EncryptionRecord record = findVaultRecord(ownerId, id);
        try {
            storage.delete(id);
        } catch (RuntimeException e) {
            log.error("Could not delete encrypted file for record {}", id, e);
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "STORAGE_ERROR",
                    "The file could not be deleted. Please try again.", e);
        }
        records.delete(record);
        log.info("Vault file deleted (record={})", id);
    }

    private EncryptionRecord findVaultRecord(long ownerId, UUID id) {
        return records.findByPublicIdAndOwner_Id(id, ownerId)
                .filter(r -> r.getStorageMode() == StorageMode.VAULT)
                .orElseThrow(ApiException::notFound);
    }

    private static EncryptedFileHeader readAndCheckHeader(InputStream in, EncryptionRecord record)
            throws IOException {
        EncryptedFileHeader header = EncryptedFileHeader.read(in);
        if (!header.recordId().equals(record.getPublicId()) || header.version() != record.getFormatVersion()) {
            throw new CryptoException("Stored file header does not match its record");
        }
        return header;
    }

    private void compensateDelete(UUID id) {
        try {
            storage.delete(id);
        } catch (RuntimeException e) {
            log.error("Orphaned encrypted file could not be removed (record={})", id, e);
        }
    }
}
