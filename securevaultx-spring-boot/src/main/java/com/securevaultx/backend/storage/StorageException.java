package com.securevaultx.backend.storage;

/** Unexpected failure of the encrypted-file store. Messages never contain filesystem paths. */
public class StorageException extends RuntimeException {

    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }

    public StorageException(String message) {
        super(message);
    }
}
