package org.ctc.backup.exception;

import java.nio.file.Path;
import java.util.UUID;

/**
 * The database import committed, but the uploads tree was not replaced.
 *
 * <p>The post-commit listener throws it to log the failed step. {@code BackupImportCoordinator}
 * throws it with audit id and recovery directory so the caller can report the partial failure.
 */
public class UploadsRestoreException extends RuntimeException {

    private final UUID auditUuid;
    private final Path recoveryDir;

    public UploadsRestoreException(String message) {
        this(message, (Throwable) null);
    }

    public UploadsRestoreException(String message, Throwable cause) {
        super(message, cause);
        this.auditUuid = null;
        this.recoveryDir = null;
    }

    public UploadsRestoreException(UUID auditUuid, Path recoveryDir, String message) {
        super(message);
        this.auditUuid = auditUuid;
        this.recoveryDir = recoveryDir;
    }

    public UUID getAuditUuid() {
        return auditUuid;
    }

    /** Directory holding {@code auto-backup-before-import.zip}; {@code null} when unknown. */
    public Path getRecoveryDir() {
        return recoveryDir;
    }
}
