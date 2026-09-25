package com.securevaultx.backend.services;

import com.securevaultx.backend.crypto.ChunkedFileCipher;
import com.securevaultx.backend.crypto.CryptoException;
import com.securevaultx.backend.crypto.EncryptedFileHeader;
import com.securevaultx.backend.crypto.KeyWrapper;
import com.securevaultx.backend.crypto.MasterKeyProvider;
import com.securevaultx.backend.crypto.WrappedKey;
import com.securevaultx.backend.entities.EncryptionRecord;
import com.securevaultx.backend.exception.ApiException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Envelope encryption facade: every file gets its own random data key, which is stored only in wrapped form.
 * Stateless: all key material lives in objects owned by a single operation (safe for concurrent users).
 */
@Service
public class EnvelopeCryptoService {

    private static final Logger log = LoggerFactory.getLogger(EnvelopeCryptoService.class);

    /** Everything needed to encrypt one new file and persist how to decrypt it later. */
    public record Prepared(UUID recordId, SecretKey dataKey, WrappedKey wrappedKey, String masterKeyId,
                           EncryptedFileHeader header) {
    }

    private final MasterKeyProvider masterKeys;
    private final KeyWrapper keyWrapper;
    private final ChunkedFileCipher cipher;

    public EnvelopeCryptoService(MasterKeyProvider masterKeys, KeyWrapper keyWrapper, ChunkedFileCipher cipher) {
        this.masterKeys = masterKeys;
        this.keyWrapper = keyWrapper;
        this.cipher = cipher;
    }

    /** New random data key (never reused), wrapped under the current master key with a fresh nonce. */
    public Prepared prepare(UUID recordId) {
        SecretKey dataKey = keyWrapper.newDataKey();
        String keyId = masterKeys.currentKeyId();
        WrappedKey wrapped = keyWrapper.wrap(masterKeys.key(keyId), dataKey, recordId,
                EncryptedFileHeader.CURRENT_VERSION);
        return new Prepared(recordId, dataKey, wrapped, keyId, cipher.newHeader(recordId));
    }

    public long encrypt(Prepared prepared, InputStream in, OutputStream out) throws IOException {
        return cipher.encrypt(prepared.dataKey(), prepared.header(), in, out);
    }

    public long encryptedLength(long plaintextLength) {
        return cipher.encryptedLength(plaintextLength);
    }

    /** Unlocks the data key of an existing record. A failure here is a server-side configuration problem. */
    public SecretKey unwrap(EncryptionRecord record) {
        try {
            return keyWrapper.unwrap(masterKeys.key(record.getWrapKeyId()),
                    new WrappedKey(record.getWrappedKey(), record.getWrapNonce()),
                    record.getPublicId(), record.getFormatVersion());
        } catch (CryptoException e) {
            log.error("Could not unwrap data key for record {} (master key changed or record tampered?)",
                    record.getPublicId());
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "KEY_UNWRAP_FAILED",
                    "The server could not unlock this file. Please contact the administrator.", e);
        }
    }

    /** Authenticates and decrypts the body of a file whose header was already read from {@code in}. */
    public long decryptBody(SecretKey dataKey, EncryptedFileHeader header, InputStream in, OutputStream out)
            throws IOException {
        return cipher.decryptBody(dataKey, header, in, out);
    }
}
