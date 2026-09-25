package com.securevaultx.backend.entities;

public enum StorageMode {
    /** Encrypted file is kept in the server vault (size-limited by policy). */
    VAULT,
    /** Server only keeps the metadata + wrapped key; the user keeps the .enc file. */
    DOWNLOAD_ONLY
}
