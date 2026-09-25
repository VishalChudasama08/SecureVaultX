package com.securevaultx.backend.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Metadata for one encrypted file (either storage mode). Holds the WRAPPED data key only; the raw key
 * exists solely in memory during an operation. No equals/hashCode/toString on purpose.
 */
@Entity
@Table(name = "encryption_records")
public class EncryptionRecord {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/** Public identifier (also embedded in the .enc header). Security never relies on it being secret. */
	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(name = "public_id", nullable = false, unique = true, updatable = false, length = 36)
	private UUID publicId;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "owner_id", nullable = false, updatable = false)
	private User owner;

	@Column(name = "original_filename", nullable = false, length = 255)
	private String originalFilename;

	@Column(name = "original_size", nullable = false)
	private long originalSize;

	@Enumerated(EnumType.STRING)
	@Column(name = "storage_mode", nullable = false, length = 16)
	private StorageMode storageMode;

	@Column(name = "format_version", nullable = false)
	private short formatVersion;

	@Column(name = "wrap_key_id", nullable = false, length = 32)
	private String wrapKeyId;

	@JdbcTypeCode(SqlTypes.VARBINARY)
	@Column(name = "wrapped_key", nullable = false, length = 64)
	private byte[] wrappedKey;

	@JdbcTypeCode(SqlTypes.VARBINARY)
	@Column(name = "wrap_nonce", nullable = false, length = 12)
	private byte[] wrapNonce;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected EncryptionRecord() {
		// for JPA
	}

	public EncryptionRecord(UUID publicId, User owner, String originalFilename, long originalSize,
			StorageMode storageMode, int formatVersion, String wrapKeyId, byte[] wrappedKey, byte[] wrapNonce) {
		this.publicId = publicId;
		this.owner = owner;
		this.originalFilename = originalFilename;
		this.originalSize = originalSize;
		this.storageMode = storageMode;
		this.formatVersion = (short) formatVersion;
		this.wrapKeyId = wrapKeyId;
		this.wrappedKey = wrappedKey.clone();
		this.wrapNonce = wrapNonce.clone();
	}

	@PrePersist
	void onCreate() {
		createdAt = Instant.now();
	}

	public UUID getPublicId() {
		return publicId;
	}

	public String getOriginalFilename() {
		return originalFilename;
	}

	public long getOriginalSize() {
		return originalSize;
	}

	public StorageMode getStorageMode() {
		return storageMode;
	}

	public int getFormatVersion() {
		return formatVersion;
	}

	public String getWrapKeyId() {
		return wrapKeyId;
	}

	public byte[] getWrappedKey() {
		return wrappedKey.clone();
	}

	public byte[] getWrapNonce() {
		return wrapNonce.clone();
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
