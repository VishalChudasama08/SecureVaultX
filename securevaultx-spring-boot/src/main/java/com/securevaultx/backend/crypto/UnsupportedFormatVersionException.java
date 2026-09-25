package com.securevaultx.backend.crypto;

/** The file is a SecureVaultX file, but of a format version this build cannot read. */
public class UnsupportedFormatVersionException extends InvalidEncryptedFileException {

    private final int version;

    public UnsupportedFormatVersionException(int version) {
        super("Unsupported encrypted-file format version: " + version);
        this.version = version;
    }

    public int version() {
        return version;
    }
}
