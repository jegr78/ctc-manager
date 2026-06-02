package org.ctc.backup.service;

import org.ctc.backup.exception.RestoreFailureSimulatedException;
import org.ctc.backup.restore.RestoreFailureInjector;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Test-only injector that throws
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
 * {@code 500} of {@code race_results}, which is the largest fixture table on Season 2023
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
     * Test-scope configuration that replaces the production
     * {@link org.ctc.backup.restore.NoopRestoreFailureInjector} bean with a
     * {@link FailAtTableInjector} targeting row 500 of {@code race_results} (~50 % mid-point of
     * the largest restore table per RESEARCH Assumption A1 — Season 2023 has ~1000 race-result
     * rows).
     *
     * <p><b>Bean-name override discipline:</b> the bean is declared with the same Spring bean
     * name as the production {@code NoopRestoreFailureInjector @Component}
     * ({@code "noopRestoreFailureInjector"}, derived from the class name lowercase-first per
     * the default {@code AnnotationBeanNameGenerator}). The test class enables
     * {@code spring.main.allow-bean-definition-overriding=true} via
     * {@code @TestPropertySource} so this {@code @TestConfiguration}-supplied definition
     * overrides the production one. Marking it {@link Primary} keeps the
     * {@code RestoreFailureInjector} autowire deterministic even if a future refactor adds
     * a third bean — the test path stays explicit about which bean wins.
     *
     * <p>Tests opt-in via {@code @Import(FailAtTableInjector.Config.class)} on the
     * {@code @SpringBootTest} class plus
     * {@code @TestPropertySource(properties = "spring.main.allow-bean-definition-overriding=true")}.
     */
    @TestConfiguration
    public static class Config {

        /**
         * Provides the {@link Primary} {@link RestoreFailureInjector} bean for tests using the
         * same bean name as the production {@code NoopRestoreFailureInjector @Component} so
         * Spring's bean-definition-override (enabled by the test's
         * {@code @TestPropertySource}) replaces the production bean rather than coexist with
         * it (which would otherwise trigger
         * {@code NoUniqueBeanDefinitionException: more than one 'primary' bean found}).
         *
         * @return a {@link FailAtTableInjector} targeting {@code race_results:500}
         */
        @Bean(name = "noopRestoreFailureInjector")
        @Primary
        public RestoreFailureInjector failAtTable() {
            return new FailAtTableInjector("race_results", 500);
        }
    }
}
