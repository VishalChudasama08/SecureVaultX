package com.securevaultx.backend.services;

import com.securevaultx.backend.storage.FileStorage;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Collects temp files left behind by crashed or cancelled uploads. */
@Component
public class StorageMaintenance {

    private static final Logger log = LoggerFactory.getLogger(StorageMaintenance.class);
    private static final Duration STALE_AFTER = Duration.ofHours(2);

    private final FileStorage storage;

    public StorageMaintenance(FileStorage storage) {
        this.storage = storage;
    }

    @Scheduled(initialDelayString = "PT1M", fixedDelayString = "PT1H")
    public void purgeStaleTempFiles() {
        try {
            int removed = storage.purgeStaleTempFiles(STALE_AFTER);
            if (removed > 0) {
                log.info("Removed {} stale temp upload file(s)", removed);
            }
        } catch (RuntimeException e) {
            log.warn("Temp-file purge failed: {}", e.getMessage());
        }
    }
}
