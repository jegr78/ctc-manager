package org.ctc.backup.service;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Hands the post-commit uploads outcome from {@link BackupImportPostCommitListener} to
 * {@link BackupImportCoordinator}; Spring swallows exceptions thrown after commit.
 */
@Component
public class BackupImportOutcomeRegistry {

    private final Map<UUID, UploadsRestoreOutcome> outcomes = new ConcurrentHashMap<>();

    void record(UUID auditUuid, UploadsRestoreOutcome outcome) {
        outcomes.put(auditUuid, outcome);
    }

    Optional<UploadsRestoreOutcome> take(UUID auditUuid) {
        return Optional.ofNullable(outcomes.remove(auditUuid));
    }
}
