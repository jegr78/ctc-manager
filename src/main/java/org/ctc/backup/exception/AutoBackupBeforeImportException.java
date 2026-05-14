package org.ctc.backup.exception;

import java.util.UUID;

/**
 * Phase 76 / SECU-07 — failure carrier thrown by {@code BackupImportService.execute(UUID)}
 * when the pre-import auto-backup ZIP write fails (D-14 / D-16). Subclass of
 * {@link BackupImportException} so the existing controller catch-chain captures it via
 * inheritance; the controller's NEW catch clause MUST appear BEFORE the parent
 * {@code BackupImportException} catch in the chain (Java exception-matching order —
 * RESEARCH Pitfall #3; superclass-first would shadow this new branch).
 *
 * <p>Semantic difference vs {@link BackupImportException}: NO database mutation occurred —
 * the auto-backup step runs BEFORE the wipe (Step 0.5 per D-14). The audit-row counts
 * ({@code wipedCounts}, {@code restoredCounts}) are therefore empty {@code {}} JSON objects
 * per CONTEXT D-18, and the controller flash communicates "no rollback was needed" rather
 * than the generic "rolled back" wording (D-17).
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
     * so {@code auditWritten} is always explicit (WR-03 double-failure path).
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
