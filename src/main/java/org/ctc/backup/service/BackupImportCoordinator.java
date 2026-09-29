package org.ctc.backup.service;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.ctc.backup.dto.BackupImportResult;
import org.ctc.backup.exception.UploadsRestoreException;
import org.springframework.stereotype.Service;

/**
 * Runs a replace-all import and reports success only when the database and the uploads tree are
 * both restored.
 */
@Service
@RequiredArgsConstructor
public class BackupImportCoordinator {

    private final BackupImportService backupImportService;
    private final BackupImportOutcomeRegistry outcomeRegistry;

    /**
     * @throws UploadsRestoreException when the database committed but the uploads were not replaced
     * @throws org.ctc.backup.exception.BackupImportException when the import aborted or rolled back
     */
    public BackupImportResult execute(UUID stagingId) {
        BackupImportResult result = backupImportService.execute(stagingId);
        UploadsRestoreOutcome outcome = outcomeRegistry.take(result.auditUuid())
                .orElseGet(() -> UploadsRestoreOutcome.failed(null, "the uploads swap did not report a result"));
        if (!outcome.restored()) {
            throw new UploadsRestoreException(result.auditUuid(), outcome.recoveryDir(), outcome.failure());
        }
        return result;
    }
}
