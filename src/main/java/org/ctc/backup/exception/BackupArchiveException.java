package org.ctc.backup.exception;

/**
 * Runtime exception thrown by ZIP hardening primitives and their callers when a backup archive
 * violates a security or structural constraint.
 *
 * <p>Every reject path in the backup import pipeline routes through this exception; the
 * {@link Reason} enum allows the controller to branch on the {@link #reason()}
 * value and select the appropriate Flash string without inspecting the message text.
 *
 * <p>Structural template mirrors {@code org.ctc.domain.exception.BusinessRuleException}
 * (single-field, no Lombok, no Spring annotations).
 */
public class BackupArchiveException extends RuntimeException {

    /**
     * Identifies the kind of constraint violation.
     *
     * <p>Values MUST remain stable — downstream code references them by name at call sites.
     */
    public enum Reason {
        /**
         * A ZIP-entry name resolved outside the base directory after
         * {@code toAbsolutePath().normalize()} (ZIP-Slip), or was an absolute path.
         * Thrown by {@code PathTraversalGuard.assertWithin}.
         */
        PATH_TRAVERSAL,

        /**
         * A single inflated entry exceeded {@code BackupImportLimits.MAX_ENTRY_BYTES}
         * (52 428 800 bytes). Thrown by {@code LimitedInputStream}.
         */
        ENTRY_TOO_LARGE,

        /**
         * The sum of all inflated entry bytes exceeded {@code BackupImportLimits.MAX_TOTAL_BYTES}
         * (524 288 000 bytes).
         */
        TOTAL_TOO_LARGE,

        /**
         * The archive contained more entries than {@code BackupImportLimits.MAX_ENTRIES}
         * (50 000).
         */
        TOO_MANY_ENTRIES,

        /**
         * The archive did not contain a {@code manifest.json} entry.
         */
        MANIFEST_MISSING,

        /**
         * A JSON structural rejection occurred anywhere inside the backup archive.
         *
         * <p><strong>Dual scope:</strong> this value covers BOTH the {@code manifest.json} file
         * itself (parse failure, missing required field, wrong shape) AND any {@code data/*.json}
         * entry whose top-level JSON token is not {@code START_ARRAY}. The
         * {@code countDataEntries} method reuses this {@code Reason} when the first token
         * assertion on a {@code data/<entity>.json} entry fails. There is intentionally no
         * separate {@code DATA_NOT_ARRAY} enum value — it would not change the UX. Maintainers
         * MUST NOT narrow the meaning of this constant to manifest-only; the broader scope
         * is load-bearing at the countDataEntries call sites.
         */
        MANIFEST_INVALID,

        /**
         * The manifest's {@code schemaVersion} field does not match the supported version.
         * Shortened from {@code SCHEMA_VERSION_MISMATCH} for cleaner call-site code.
         */
        SCHEMA_MISMATCH,

        /**
         * The uploaded file is not a valid ZIP archive (magic bytes check failed).
         * Thrown by {@code BackupImportService.stage()} when the first four bytes are
         * not {@code 0x50 0x4B 0x03 0x04}.
         */
        NOT_A_ZIP
    }

    private final Reason reason;

    /**
     * Constructs a new {@code BackupArchiveException} with the given reason and message.
     *
     * @param reason  the constraint that was violated (never {@code null})
     * @param message human-readable description (may be logged or shown in dev mode)
     */
    public BackupArchiveException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    /**
     * Constructs a new {@code BackupArchiveException} with the given reason, message, and cause.
     *
     * @param reason  the constraint that was violated (never {@code null})
     * @param message human-readable description
     * @param cause   underlying exception (e.g. {@code JsonParseException})
     */
    public BackupArchiveException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    /**
     * Returns the reason that triggered this exception.
     *
     * @return the {@link Reason} value; never {@code null}
     */
    public Reason reason() {
        return reason;
    }
}
