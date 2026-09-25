-- SecureVaultX schema, version 1.
-- Portable SQL: runs on MySQL 8.x (production) and on H2 in MySQL mode (tests).

CREATE TABLE users (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    full_name     VARCHAR(100) NOT NULL,
    email         VARCHAR(254) NOT NULL,   -- stored lower-cased; the login identity
    password_hash VARCHAR(100) NOT NULL,   -- BCrypt hash (60 chars today); never the password
    created_at    DATETIME(6)  NOT NULL,   -- UTC
    updated_at    DATETIME(6)  NOT NULL,   -- UTC
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uq_users_email UNIQUE (email)
);

-- One row per encrypted file, for BOTH storage modes. The encrypted bytes themselves are NOT here:
-- vault files live on disk as <public_id>.enc, download-only files are not kept by the server at all.
CREATE TABLE encryption_records (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    public_id         CHAR(36)     NOT NULL,   -- random UUID; also written into the .enc header
    owner_id          BIGINT       NOT NULL,
    original_filename VARCHAR(255) NOT NULL,   -- sanitised display name only, never used as a path
    original_size     BIGINT       NOT NULL,   -- plaintext bytes
    storage_mode      VARCHAR(16)  NOT NULL,   -- VAULT | DOWNLOAD_ONLY
    format_version    SMALLINT     NOT NULL,   -- encrypted-file format version (see docs/ARCHITECTURE.md)
    wrap_key_id       VARCHAR(32)  NOT NULL,   -- which master key wrapped the data key (future rotation)
    wrapped_key       VARBINARY(64) NOT NULL,  -- AES-GCM(master key, data key): 32 + 16 tag bytes
    wrap_nonce        VARBINARY(12) NOT NULL,  -- unique random nonce of the key-wrap operation
    created_at        DATETIME(6)  NOT NULL,   -- UTC
    CONSTRAINT pk_encryption_records PRIMARY KEY (id),
    CONSTRAINT uq_encryption_records_public_id UNIQUE (public_id),
    CONSTRAINT fk_encryption_records_owner FOREIGN KEY (owner_id) REFERENCES users (id),
    CONSTRAINT ck_encryption_records_mode CHECK (storage_mode IN ('VAULT', 'DOWNLOAD_ONLY'))
);

CREATE INDEX idx_encryption_records_owner_mode_created
    ON encryption_records (owner_id, storage_mode, created_at);
