package org.ctc.backup.exception;

import java.util.UUID;

/**
 * Phase 75 / Plan 06 — failure carrier thrown by {@code BackupImportService.execute(UUID)}
 * (D-14) when the wipe-and-restore @Transactional method rolls back.
 *
 * <p>Carries the {@code auditUuid} of the {@code data_import_audit} row written via
 * {@code DataImportAuditService.recordResult(success=false, ...)} on the catch-path (REQUIRES_NEW;
 * survives the outer rollback per D-01). The controller (Plan 08) binds this UUID into the
 * locked D-15 #2 failure-flash placeholder:
 *
 * <pre>"Import failed and was rolled back — see logs. Audit-id: {auditUuid}."</pre>
 *
 * <p>Unchecked because the throw site lives inside Spring's {@code @Transactional} machinery,
 * which does not tolerate checked-exception signatures on declared rollback paths. Mirrors the
 * structural template of {@link BackupArchiveException} but without a {@code Reason} enum —
 * there is only one failure mode here ("import rolled back"); the actual cause (wipe failure,
 * restore failure, JSON parse failure, etc.) is carried in {@link #getCause()} and the SLF4J
 * ERROR log line at the service catch-block.
 *
 * <p>Plan 06 is the throw site; Plan 08 is the catch site (controller). The
 * {@link #getAuditUuid()} accessor is the only public contract the controller depends on.
 */
public class BackupImportException extends RuntimeException {

    private final UUID auditUuid;

    /**
     * Constructs a new {@code BackupImportException} with the given audit-row UUID and root
     * cause. The exception message is synthesized from the cause's message so the SLF4J log
     * line carries useful diagnostic context.
     *
     * @param auditUuid id of the {@code data_import_audit} row recorded via REQUIRES_NEW
     * @param cause     the underlying exception that triggered the rollback
     */
    public BackupImportException(UUID auditUuid, Throwable cause) {
        super("Backup import failed and was rolled back: " + describe(cause), cause);
        this.auditUuid = auditUuid;
    }

    /**
     * Constructs a new {@code BackupImportException} with an explicit message and root cause.
     *
     * @param auditUuid id of the {@code data_import_audit} row recorded via REQUIRES_NEW
     * @param message   human-readable description (may be logged)
     * @param cause     the underlying exception that triggered the rollback
     */
    public BackupImportException(UUID auditUuid, String message, Throwable cause) {
        super(message, cause);
        this.auditUuid = auditUuid;
    }

    /**
     * Returns the {@code data_import_audit} row UUID. The controller surfaces this in the
     * D-15 #2 failure-flash message so the operator can query the row directly.
     *
     * @return the audit-row UUID, never {@code null}
     */
    public UUID getAuditUuid() {
        return auditUuid;
    }

    private static String describe(Throwable cause) {
        if (cause == null) {
            return "<no cause>";
        }
        String msg = cause.getMessage();
        if (msg == null || msg.isBlank()) {
            return cause.getClass().getSimpleName();
        }
        return cause.getClass().getSimpleName() + ": " + msg;
    }
}
