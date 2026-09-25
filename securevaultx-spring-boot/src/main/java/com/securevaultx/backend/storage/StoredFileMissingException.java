package com.securevaultx.backend.storage;

/** A database record exists but its encrypted file is not on disk. */
public class StoredFileMissingException extends StorageException {

    public StoredFileMissingException(String message) {
        super(message);
    }
}
