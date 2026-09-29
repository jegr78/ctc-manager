package org.ctc.backup.exception;

import java.util.UUID;

/**
 * Thrown by {@code BackupImportService.execute(UUID)} when {@code UploadsSwapPreflight} rejects the
 * uploads layout. Raised before the auto-backup and the wipe, so the database is unchanged.
 */
public class UploadsPreflightFailedException extends BackupImportException {

    public UploadsPreflightFailedException(UUID auditUuid, boolean auditWritten, UploadsSwapPreflightException cause) {
        super(auditUuid, auditWritten, cause);
    }
}
