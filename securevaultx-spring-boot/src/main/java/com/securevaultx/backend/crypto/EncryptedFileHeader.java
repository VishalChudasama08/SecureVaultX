package com.securevaultx.backend.crypto;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.UUID;

/**
 * Non-secret, versioned header at the start of every SecureVaultX encrypted file.
 *
 * <pre>
 * offset  size  field
 *   0      4    magic            "SVXF"
 *   4      1    format version   0x01
 *   5      1    reserved         must be 0x00
 *   6      4    chunk size       plaintext bytes per chunk, big-endian unsigned
 *  10     16    record UUID      identifies the encryption record (big-endian msb, lsb)
 *  26      7    nonce prefix     random, per file
 *  ---   33    total
 * </pre>
 *
 * The whole header is fed to AES-GCM as AAD for every chunk, so it cannot be altered undetected.
 */
public record EncryptedFileHeader(int version, int chunkSize, UUID recordId, byte[] noncePrefix) {

    public static final byte[] MAGIC = {'S', 'V', 'X', 'F'};
    public static final int CURRENT_VERSION = 1;
    public static final int NONCE_PREFIX_LENGTH = 7;
    public static final int LENGTH = 4 + 1 + 1 + 4 + 16 + NONCE_PREFIX_LENGTH;
    /** Upper bound accepted when reading, so a hostile header cannot force huge buffers. */
    public static final int MAX_CHUNK_SIZE = 1024 * 1024;

    public EncryptedFileHeader {
        if (chunkSize < 1 || chunkSize > MAX_CHUNK_SIZE) {
            throw new InvalidEncryptedFileException("Invalid chunk size in header");
        }
        if (recordId == null || noncePrefix == null || noncePrefix.length != NONCE_PREFIX_LENGTH) {
            throw new InvalidEncryptedFileException("Malformed header");
        }
        noncePrefix = noncePrefix.clone();
    }

    @Override
    public byte[] noncePrefix() {
        return noncePrefix.clone();
    }

    public byte[] toBytes() {
        ByteBuffer buf = ByteBuffer.allocate(LENGTH);
        buf.put(MAGIC);
        buf.put((byte) version);
        buf.put((byte) 0);
        buf.putInt(chunkSize);
        buf.putLong(recordId.getMostSignificantBits());
        buf.putLong(recordId.getLeastSignificantBits());
        buf.put(noncePrefix);
        return buf.array();
    }

    /** Reads and validates a header from the current position of the stream. */
    public static EncryptedFileHeader read(InputStream in) throws IOException {
        byte[] raw = in.readNBytes(LENGTH);
        if (raw.length < LENGTH) {
            throw new InvalidEncryptedFileException("File is too short to be a SecureVaultX encrypted file");
        }
        if (!Arrays.equals(Arrays.copyOfRange(raw, 0, 4), MAGIC)) {
            throw new InvalidEncryptedFileException("Not a SecureVaultX encrypted file");
        }
        int version = raw[4] & 0xFF;
        if (version != CURRENT_VERSION) {
            throw new UnsupportedFormatVersionException(version);
        }
        if (raw[5] != 0) {
            throw new InvalidEncryptedFileException("Malformed header");
        }
        ByteBuffer buf = ByteBuffer.wrap(raw, 6, LENGTH - 6);
        int chunkSize = buf.getInt();
        UUID id = new UUID(buf.getLong(), buf.getLong());
        byte[] prefix = new byte[NONCE_PREFIX_LENGTH];
        buf.get(prefix);
        return new EncryptedFileHeader(version, chunkSize, id, prefix);
    }
}
