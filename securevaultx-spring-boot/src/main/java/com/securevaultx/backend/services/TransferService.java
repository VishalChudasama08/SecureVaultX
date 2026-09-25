package com.securevaultx.backend.services;

import com.securevaultx.backend.config.StorageProperties;
import com.securevaultx.backend.crypto.EncryptedFileHeader;
import com.securevaultx.backend.crypto.InvalidEncryptedFileException;
import com.securevaultx.backend.entities.EncryptionRecord;
import com.securevaultx.backend.entities.StorageMode;
import com.securevaultx.backend.exception.ApiException;
import com.securevaultx.backend.repositories.EncryptionRecordRepository;
import com.securevaultx.backend.repositories.UserRepository;
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
import java.util.UUID;
import javax.crypto.SecretKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Mode B: "Encrypt &amp; Download". The server streams the encrypted file back and keeps NO file: only the record
 * (owner + wrapped key) so the owner can decrypt the .enc later. Works for any size up to the upload limit.
 */
@Service
public class TransferService {

    private static final Logger log = LoggerFactory.getLogger(TransferService.class);

    private final EncryptionRecordRepository records;
    private final UserRepository users;
    private final FileStorage storage;
    private final EnvelopeCryptoService crypto;
    private final StorageProperties props;

    public TransferService(EncryptionRecordRepository records, UserRepository users, FileStorage storage,
                           EnvelopeCryptoService crypto, StorageProperties props) {
        this.records = records;
        this.users = users;
        this.storage = storage;
        this.crypto = crypto;
        this.props = props;
    }

    /**
     * Creates the record, then returns a streaming download that encrypts while sending (constant memory, no
     * plaintext on disk). If sending fails the record is removed, because the user never received a usable file.
     * A Content-Length is required so the response length is exact and truncation is detectable by the client.
     */
    public StreamedDownload encryptForDownload(long ownerId, String rawFilename, long contentLength,
                                               InputStream body) {
        if (contentLength < 0) {
            throw ApiException.lengthRequired();
        }
        long maxUpload = props.maxUploadSize().toBytes();
        if (contentLength > maxUpload) {
            throw ApiException.uploadTooLarge(maxUpload);
        }
        UUID id = UUID.randomUUID();
        EnvelopeCryptoService.Prepared prepared = crypto.prepare(id);
        String filename = FilenameSanitizer.sanitize(rawFilename);

        EncryptionRecord record = records.save(new EncryptionRecord(id, users.getReferenceById(ownerId), filename,
                contentLength, StorageMode.DOWNLOAD_ONLY, EncryptedFileHeader.CURRENT_VERSION,
                prepared.masterKeyId(), prepared.wrappedKey().ciphertext(), prepared.wrappedKey().nonce()));

        return new StreamedDownload(filename + ".enc", crypto.encryptedLength(contentLength), out -> {
            InputStream in = new LimitedInputStream(body, contentLength, () -> ApiException.uploadTooLarge(maxUpload));
            long n = crypto.encrypt(prepared, in, out);
            if (n != contentLength) {
                throw new IOException("Upload ended before the declared Content-Length");
            }
            log.info("Encrypt & Download completed (record={}, bytes={})", id, n);
        }, () -> {
            log.warn("Encrypt & Download failed mid-stream; removing record {}", id);
            try {
                records.delete(record);
            } catch (RuntimeException e) {
                log.error("Could not remove record {} after failed stream", id, e);
            }
        }, () -> { });
    }

    /**
     * Decrypts a previously downloaded .enc that the client uploads again. The ciphertext is spooled to a temp
     * file (it is encrypted, so no plaintext touches the disk), ownership is verified through the record id in
     * the header, the whole file is authenticated, and only then is plaintext streamed out.
     */
    public StreamedDownload decryptUpload(long ownerId, InputStream body, long contentLength) {
        long maxUpload = props.maxUploadSize().toBytes();
        if (contentLength > maxUpload) {
            throw ApiException.uploadTooLarge(maxUpload);
        }
        Path temp = null;
        boolean handedOver = false;
        try {
            temp = storage.newTempFile();
            try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(temp));
                 InputStream in = new LimitedInputStream(body, maxUpload, () -> ApiException.uploadTooLarge(maxUpload))) {
                in.transferTo(out);
            }

            final Path file = temp;
            EncryptedFileHeader header;
            try (InputStream in = new BufferedInputStream(Files.newInputStream(file))) {
                header = EncryptedFileHeader.read(in);
            }
            // Ownership: unknown id and someone else's id are indistinguishable (both 404).
            EncryptionRecord record = records.findByPublicIdAndOwner_Id(header.recordId(), ownerId)
                    .orElseThrow(ApiException::notFound);
            if (header.version() != record.getFormatVersion()) {
                throw new InvalidEncryptedFileException("Header does not match the encryption record");
            }
            SecretKey key = crypto.unwrap(record);

            // Pass 1: authenticate the entire file, discarding plaintext. Any tampering/truncation throws here.
            try (InputStream in = new BufferedInputStream(Files.newInputStream(file))) {
                EncryptedFileHeader h = EncryptedFileHeader.read(in);
                long n = crypto.decryptBody(key, h, in, OutputStream.nullOutputStream());
                if (n != record.getOriginalSize()) {
                    throw new InvalidEncryptedFileException("Decrypted size does not match the record");
                }
            }

            handedOver = true;
            return new StreamedDownload(record.getOriginalFilename(), record.getOriginalSize(), out -> {
                // Pass 2: release plaintext
                try (InputStream in = new BufferedInputStream(Files.newInputStream(file))) {
                    EncryptedFileHeader h = EncryptedFileHeader.read(in);
                    crypto.decryptBody(key, h, in, out);
                }
            }, () -> { }, () -> storage.discardTemp(file));
        } catch (IOException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "STORAGE_ERROR",
                    "The uploaded file could not be processed.", e);
        } finally {
            if (!handedOver) {
                storage.discardTemp(temp);
            }
        }
    }
}
