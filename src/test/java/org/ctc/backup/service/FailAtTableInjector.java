package org.ctc.backup.service;

import org.ctc.backup.exception.RestoreFailureSimulatedException;
import org.ctc.backup.restore.RestoreFailureInjector;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Phase 75 / Plan 09 — test-only injector that throws
 * {@link RestoreFailureSimulatedException} when the restore loop reaches a configured
 * {@code (tableName, rowIndex)} target.
 *
 * <p>The injector is the production-shipped {@link RestoreFailureInjector} SPI from Plan 75-01
 * (D-13), driven into the
 * {@link org.ctc.backup.service.BackupImportService#execute(java.util.UUID)} batch loop via
 * the {@link #failAtTable()} bean below (see {@link Config}). The production
 * {@link org.ctc.backup.restore.NoopRestoreFailureInjector} stays on the classpath; this
 * bean is annotated {@link Primary} so Spring resolves the test-scope override ahead of the
 * production default whenever a test {@code @Import(Config.class)}-s this configuration.
 *
 * <p>{@code BackupImportRollbackIT} is the primary consumer — the configured target is row
 * {@code 500} of {@code race_results}, which is the largest fixture table on Saison 2023
 * (RESEARCH Assumption A1) and lands at the ~50 % mid-point of the restore stream so the
 * outer {@code @Transactional} boundary has wiped + partially-restored when the failure
 * fires. The wipe-rollback assertion battery in the IT verifies the entire DB returns to its
 * pre-import state and that the REQUIRES_NEW {@code data_import_audit} row with
 * {@code success=false} survives the outer rollback (Plan 75-02 contract).
 *
 * <p>The class lives under {@code src/test/java} (not {@code src/main/java}) — only test
 * code instantiates it, and the throw site is deliberately invisible to the production
 * classpath. The injection point itself (the {@code maybeFailAt} call inside
 * {@code BackupImportService.restoreAll}) is production code, observable in production logs
 * via a plain method invocation rather than AOP magic.
 */
public class FailAtTableInjector implements RestoreFailureInjector {

    private final String targetTable;
    private final int targetRow;

    /**
     * Constructs a new injector that throws when the restore loop reports a row at
     * {@code (targetTable, targetRow)}.
     *
     * @param targetTable snake_case table name (matches
     *                    {@link org.ctc.backup.restore.EntityRestorer#tableName()})
     * @param targetRow   zero-based row index at which the injector fires
     */
    public FailAtTableInjector(String targetTable, int targetRow) {
        this.targetTable = targetTable;
        this.targetRow = targetRow;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Throws {@link RestoreFailureSimulatedException} when both {@code tableName} matches
     * {@link #targetTable} (case-sensitive snake_case equality) AND {@code rowIndex} equals
     * {@link #targetRow}. Any other combination is a no-op.
     */
    @Override
    public void maybeFailAt(String tableName, int rowIndex) {
        if (targetTable.equals(tableName) && rowIndex == targetRow) {
            throw new RestoreFailureSimulatedException(
                    "Simulated mid-restore failure at " + tableName + ":" + rowIndex);
        }
    }

    /**
     * Test-scope configuration that wires {@link FailAtTableInjector} as the {@link Primary}
     * {@link RestoreFailureInjector} bean.
     *
     * <p>Targets row 500 of {@code race_results} — Saison 2023 has ~1000 race-result rows
     * per RESEARCH Assumption A1, so row 500 is roughly the 50 % mid-point of the largest
     * restore table. The wipe is complete by then; the restore stream has partially-inserted
     * rows that the outer {@code @Transactional} rollback then undoes.
     *
     * <p>Tests opt-in via {@code @Import(FailAtTableInjector.Config.class)} on the
     * {@code @SpringBootTest} class.
     */
    @TestConfiguration
    public static class Config {

        /**
         * Provides the {@link Primary} {@link RestoreFailureInjector} bean for tests.
         *
         * @return a {@link FailAtTableInjector} targeting {@code race_results:500}
         */
        @Bean
        @Primary
        public RestoreFailureInjector failAtTable() {
            return new FailAtTableInjector("race_results", 500);
        }
    }
}
