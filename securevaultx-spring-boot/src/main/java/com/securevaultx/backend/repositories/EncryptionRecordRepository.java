package com.securevaultx.backend.repositories;

import com.securevaultx.backend.entities.EncryptionRecord;
import com.securevaultx.backend.entities.StorageMode;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EncryptionRecordRepository extends JpaRepository<EncryptionRecord, Long> {

	/**
	 * The ONLY way services load a record by its public id: the owner is part of the lookup, so a record that
	 * belongs to someone else is indistinguishable from one that does not exist.
	 */
	Optional<EncryptionRecord> findByPublicIdAndOwner_Id(UUID publicId, Long ownerId);

	List<EncryptionRecord> findByOwner_IdAndStorageModeOrderByCreatedAtDesc(Long ownerId, StorageMode storageMode);
}
