package org.ctc.backup.exception;

/**
 * Phase 75 D-13 — test-injection failure carrier thrown by
 * {@code FailAtTableInjector} (a {@code @TestConfiguration} implementation of
 * {@link org.ctc.backup.restore.RestoreFailureInjector}, added in Plan 75-08).
 *
 * <p>The exception type lives in the production package per the D-13 SPI discipline: the
 * injection point ({@link org.ctc.backup.restore.RestoreFailureInjector#maybeFailAt(String, int)})
 * is an extension point of the production restore loop and the production code must compile
 * against the carrier type, even though only the test-scope {@code FailAtTableInjector} ever
 * throws it. {@code BackupImportRollbackIT} (Plan 75-08) catches it and asserts that the
 * single {@code @Transactional} boundary rolled the wipe back and that the
 * {@code @Transactional(propagation=REQUIRES_NEW)} {@code data_import_audit} row with
 * {@code success=false} persisted.
 *
 * <p>Plan reference: scaffolding lives in Plan 75-01; the actual throw site (inside
 * {@code FailAtTableInjector}) ships in Plan 75-08.
 *
 * <p>Unchecked because the call site is inside the {@code restore(...)} batch loop, which
 * cannot declare a checked-exception signature without leaking test concerns into the
 * production {@link org.ctc.backup.restore.EntityRestorer} contract.
 */
public class RestoreFailureSimulatedException extends RuntimeException {

    /**
     * Constructs a new {@code RestoreFailureSimulatedException} with the given message.
     *
     * @param message human-readable description (typically of the form
     *                {@code "Simulated failure at <table> row <index>"})
     */
    public RestoreFailureSimulatedException(String message) {
        super(message);
    }

    /**
     * Constructs a new {@code RestoreFailureSimulatedException} with the given message and
     * cause.
     *
     * @param message human-readable description
     * @param cause   underlying exception (typically {@code null} — this exception is
     *                deliberately raised, not wrapping another failure)
     */
    public RestoreFailureSimulatedException(String message, Throwable cause) {
        super(message, cause);
    }
}
