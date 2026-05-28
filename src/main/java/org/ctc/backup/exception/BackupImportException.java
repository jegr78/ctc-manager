package org.ctc.backup.exception;

import java.util.UUID;

/**
 * Failure carrier thrown by {@code BackupImportService.execute(UUID)}
 * when the wipe-and-restore {@code @Transactional} method rolls back.
 *
 * <p>Carries the {@code auditUuid} of the {@code data_import_audit} row written via
 * {@code DataImportAuditService.recordResult(success=false, ...)} on the catch-path (REQUIRES_NEW;
 * survives the outer rollback). The controller binds this UUID into the failure-flash message:
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
 * <p>{@link #getAuditUuid()} is the only public contract the controller depends on.
 */
public class BackupImportException extends RuntimeException {

    private final UUID auditUuid;
    private final boolean auditWritten;

    /**
     * Constructs a new {@code BackupImportException} with the given audit-row UUID and root
     * cause. The exception message is synthesized from the cause's message so the SLF4J log
     * line carries useful diagnostic context.
     *
     * <p>This constructor assumes the audit row was written successfully. Use
     * {@link #BackupImportException(UUID, boolean, Throwable)} on the double-failure path
     * where the REQUIRES_NEW audit write itself failed and no row exists for the operator
     * to query.
     *
     * @param auditUuid id of the {@code data_import_audit} row recorded via REQUIRES_NEW
     * @param cause     the underlying exception that triggered the rollback
     */
    public BackupImportException(UUID auditUuid, Throwable cause) {
        this(auditUuid, /* auditWritten */ true, cause);
    }

    /**
     * Constructs a new {@code BackupImportException} carrying both the pre-allocated audit
     * UUID and an {@code auditWritten} flag (distinguishes "row exists, query by UUID"
     * from "audit-write itself failed, no row to query").
     *
     * @param auditUuid    pre-allocated UUID; may NOT correspond to a persisted row when
     *                     {@code auditWritten == false}
     * @param auditWritten {@code true} when the REQUIRES_NEW audit row write succeeded;
     *                     {@code false} on the double-failure path
     * @param cause        the underlying exception that triggered the rollback
     */
    public BackupImportException(UUID auditUuid, boolean auditWritten, Throwable cause) {
        super("Backup import failed and was rolled back: " + describe(cause), cause);
        this.auditUuid = auditUuid;
        this.auditWritten = auditWritten;
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
        this.auditWritten = true;
    }

    /**
     * Returns the {@code data_import_audit} row UUID. The controller surfaces this in the
     * failure-flash message so the operator can query the row directly.
     *
     * @return the audit-row UUID, never {@code null}
     */
    public UUID getAuditUuid() {
        return auditUuid;
    }

    /**
     * Returns whether the REQUIRES_NEW audit row was successfully persisted before this
     * exception was thrown. When {@code false}, the {@link #getAuditUuid()} value is the
     * pre-allocated UUID but no matching row exists in {@code data_import_audit}; the
     * controller adjusts the failure-flash text accordingly.
     *
     * @return {@code true} when an audit row exists, {@code false} on the double-failure path
     */
    public boolean isAuditWritten() {
        return auditWritten;
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
