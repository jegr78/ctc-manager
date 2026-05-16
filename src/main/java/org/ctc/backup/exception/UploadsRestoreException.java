package org.ctc.backup.exception;

/**
 * Signals failure of the post-commit {@code uploads/}-tree atomic-move triple.
 *
 * <p>Thrown by the post-commit listener when either Step-1
 * ({@code Files.move(uploads, importBackupDir.resolve("uploads-old"), ATOMIC_MOVE)}) or Step-2
 * ({@code Files.move(stagedUploadsDir, uploads, ATOMIC_MOVE)}) fails after the DB transaction
 * has already committed. The listener attempts a best-effort revert of Step-1 on Step-2
 * failure, logs the outcome, and re-throws this exception so the controller can flash the
 * soft-fail message:
 *
 * <pre>"Import database succeeded but uploads restore failed and was reverted. See logs.
 * Audit-id: {auditUuid}."</pre>
 *
 * <p>Unchecked because the call sites live inside Spring's transactional /
 * {@code @TransactionalEventListener} machinery, neither of which tolerates checked-exception
 * signatures cleanly. Mirrors the structural template of
 * {@link BackupArchiveException} but without a {@code Reason} enum — there is only one
 * failure mode here ("uploads move failed"); branching on Step-1 vs Step-2 is done via log
 * messages, not the type system.
 */
public class UploadsRestoreException extends RuntimeException {

    /**
     * Constructs a new {@code UploadsRestoreException} with the given message.
     *
     * @param message human-readable description (may be logged or shown in dev mode)
     */
    public UploadsRestoreException(String message) {
        super(message);
    }

    /**
     * Constructs a new {@code UploadsRestoreException} with the given message and cause.
     *
     * @param message human-readable description
     * @param cause   underlying I/O exception (typically {@code java.nio.file.FileSystemException}
     *                or {@code java.io.IOException})
     */
    public UploadsRestoreException(String message, Throwable cause) {
        super(message, cause);
    }
}
