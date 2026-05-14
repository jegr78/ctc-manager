package org.ctc.backup.restore;

/**
 * Phase 75 test seam (D-13) — pluggable fail-injection hook called from the restore batch loop.
 *
 * <p>The orchestrator ({@code BackupImportService.restore(...)}, added in Plan 06) invokes
 * {@link #maybeFailAt(String, int)} after every 50 rows inside each entity batch. The production
 * default ({@link NoopRestoreFailureInjector}, {@link org.springframework.context.annotation.Primary})
 * is a no-op; a test-scoped {@code @TestConfiguration} in {@code BackupImportRollbackIT} (Plan 08)
 * provides a {@code FailAtTableInjector(targetTable, targetRowIndex)} bean that throws
 * {@link org.ctc.backup.exception.RestoreFailureSimulatedException} when its targets match,
 * covering the ROADMAP-locked "fail at 50% of the largest table" scenario without
 * {@link org.mockito.Mockito#spy(Object) Mockito.spy(jdbcTemplate)} brittleness.
 *
 * <p>The single-method shape keeps the production code path observable (a plain method call,
 * not AOP) and avoids leaking test infrastructure into main: the SPI lives in the production
 * package because it IS a production extension point, even though only test code exercises the
 * non-trivial implementation.
 */
public interface RestoreFailureInjector {

    /**
     * Hook called from inside the restore batch loop after every 50 rows.
     *
     * <p>The production {@link NoopRestoreFailureInjector} returns without side effects. A
     * test-scoped {@code FailAtTableInjector} compares {@code tableName} / {@code rowIndex}
     * against its configured targets and throws
     * {@link org.ctc.backup.exception.RestoreFailureSimulatedException} on a match, triggering
     * the wipe-rollback path that {@code BackupImportRollbackIT} asserts on.
     *
     * @param tableName snake_case table currently being restored (matches
     *                  {@link EntityRestorer#tableName()})
     * @param rowIndex  zero-based index of the row about to be restored inside that table's
     *                  JSON array
     */
    void maybeFailAt(String tableName, int rowIndex);
}
