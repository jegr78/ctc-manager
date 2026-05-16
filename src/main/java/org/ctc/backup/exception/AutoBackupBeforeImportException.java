package org.ctc.backup.exception;

import java.util.UUID;

/**
 * Failure carrier thrown by {@code BackupImportService.execute(UUID)}
 * when the pre-import auto-backup ZIP write fails. Subclass of
 * {@link BackupImportException} so the existing controller catch-chain captures it via
 * inheritance; the controller's catch clause MUST appear BEFORE the parent
 * {@code BackupImportException} catch in the chain (Java exception-matching order;
 * superclass-first would shadow this branch).
 *
 * <p>Semantic difference vs {@link BackupImportException}: NO database mutation occurred —
 * the auto-backup step runs BEFORE the wipe. The audit-row counts
 * ({@code wipedCounts}, {@code restoredCounts}) are therefore empty {@code {}} JSON objects,
 * and the controller flash communicates "no rollback was needed" rather than the generic
 * "rolled back" wording.
 *
 * <p>Constructor delegation mirrors the sibling-shape pattern of {@link BackupImportException}:
 * the constructor calls {@code super(...)} directly with no new fields and no overridden
 * {@code getMessage()} — the parent's {@code describe(cause)} synthesis already produces
 * a useful log message.
 */
public class AutoBackupBeforeImportException extends BackupImportException {

    /**
     * Full-delegation constructor used by the catch block in {@code BackupImportService}.
     * The caller always knows the {@code tryRecordFailure} return value at throw time,
     * so {@code auditWritten} is always explicit.
     *
     * @param auditUuid    pre-allocated UUID; may NOT correspond to a persisted row when
     *                     {@code auditWritten == false}
     * @param auditWritten {@code true} when the REQUIRES_NEW audit row write succeeded;
     *                     {@code false} on the double-failure path
     * @param cause        the underlying exception that prevented the auto-backup ZIP write
     */
    public AutoBackupBeforeImportException(UUID auditUuid, boolean auditWritten, Throwable cause) {
        super(auditUuid, auditWritten, cause);
    }
}
