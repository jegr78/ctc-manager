package org.ctc.backup.it.support;

import org.ctc.backup.restore.RestoreFailureInjector;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Phase 76 / Plan 01 — test-only {@link RestoreFailureInjector} that blocks the restore
 * loop at a configured {@code (targetTable, targetRow)} target instead of throwing.
 *
 * <p>Mirrors the {@code FailAtTableInjector} bean-override discipline (Phase 75 D-13)
 * exactly: the inner {@link Config} class declares the bean with the SAME name as the
 * production {@code NoopRestoreFailureInjector @Component}
 * ({@code "noopRestoreFailureInjector"}) so that Spring's bean-definition-override
 * (enabled by {@code @TestPropertySource(properties = "spring.main.allow-bean-definition-overriding=true")})
 * replaces the production bean in the test application context. The {@link Primary}
 * annotation keeps the {@code RestoreFailureInjector} autowire deterministic even when
 * both the production and test beans are on the classpath.
 *
 * <p>Semantics: when the restore loop calls {@link #maybeFailAt(String, int)} at the
 * configured {@code (targetTable, targetRow)} target, this injector:
 * <ol>
 *   <li>Signals {@link #hasAcquired} ({@code countDown()}) so that the test's
 *       coordinating thread knows thread A is mid-restore inside the lock.</li>
 *   <li>Blocks on {@link #releaseLatch} ({@code await(5, SECONDS)}) so that the test
 *       thread can fire thread B's concurrent import attempt while thread A is paused.</li>
 * </ol>
 * This is a SLOW-IMPORT injector, not a failure injector: {@link #maybeFailAt} does NOT
 * throw. Thread A completes the import normally after the release latch is counted down.
 *
 * <p>Used by:
 * <ul>
 *   <li>{@code org.ctc.backup.it.ImportConcurrentLockIT} (D-20)</li>
 * </ul>
 *
 * @see org.ctc.backup.service.FailAtTableInjector — the Phase 75 analog (throws instead of blocking)
 */
public class BlockingRestoreFailureInjector implements RestoreFailureInjector {

    private final CountDownLatch hasAcquired;
    private final CountDownLatch releaseLatch;
    private final String targetTable;
    private final int targetRow;

    public BlockingRestoreFailureInjector(CountDownLatch hasAcquired,
                                          CountDownLatch releaseLatch,
                                          String targetTable,
                                          int targetRow) {
        this.hasAcquired = hasAcquired;
        this.releaseLatch = releaseLatch;
        this.targetTable = targetTable;
        this.targetRow = targetRow;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Blocks (does NOT throw) when {@code tableName} matches {@link #targetTable}
     * (case-sensitive snake_case equality) AND {@code rowIndex} equals {@link #targetRow}.
     * Any other combination is a no-op.
     */
    @Override
    public void maybeFailAt(String tableName, int rowIndex) {
        if (targetTable.equals(tableName) && rowIndex == targetRow) {
            hasAcquired.countDown();  // signal: thread A is mid-restore inside the lock
            try {
                releaseLatch.await(5, TimeUnit.SECONDS);  // block: wait for test thread to signal release
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
        }
        // else: no-op (other tables / rows pass through)
    }

    /**
     * Test-scope configuration that replaces the production
     * {@link org.ctc.backup.restore.NoopRestoreFailureInjector} bean with a
     * {@link BlockingRestoreFailureInjector} targeting row {@code 50} of
     * {@code race_results} — the Phase 75 RESEARCH Assumption A1 mid-point target, here
     * used for timing rather than failure injection.
     *
     * <p><b>Bean-name override discipline (mirrors FailAtTableInjector.Config lines 90-108):</b>
     * the bean is declared with the same Spring bean name as the production
     * {@code NoopRestoreFailureInjector @Component} ({@code "noopRestoreFailureInjector"}) so
     * Spring's bean-definition-override replaces the production one rather than coexisting
     * (which would cause {@code NoUniqueBeanDefinitionException}). The test class enables
     * the override via
     * {@code @TestPropertySource(properties = "spring.main.allow-bean-definition-overriding=true")}.
     *
     * <p>The two {@link CountDownLatch} beans ({@code hasAcquired} and {@code releaseLatch})
     * are exposed as Spring beans so that the IT class can autowire them directly.
     */
    @TestConfiguration
    public static class Config {

        @Bean
        public CountDownLatch hasAcquired() {
            return new CountDownLatch(1);
        }

        @Bean
        public CountDownLatch releaseLatch() {
            return new CountDownLatch(1);
        }

        @Bean(name = "noopRestoreFailureInjector")  // SAME NAME as the production @Component (D-20 discipline)
        @Primary
        public RestoreFailureInjector blockingInjector(CountDownLatch hasAcquired,
                                                       CountDownLatch releaseLatch) {
            return new BlockingRestoreFailureInjector(hasAcquired, releaseLatch, "race_results", 50);
        }
    }
}
