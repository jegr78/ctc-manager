package org.ctc.backup.restore;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Production no-op implementation of {@link RestoreFailureInjector} (CONTEXT D-13).
 *
 * <p>Marked {@link Primary} so that the production application context always resolves
 * {@code @Autowired RestoreFailureInjector} to this bean even when a test-scope override
 * (e.g. {@code FailAtTableInjector}) is also present on the classpath. The
 * {@code BackupImportRollbackIT} (Plan 08) supplies its own {@code @TestConfiguration} with a
 * non-{@code @Primary} {@code FailAtTableInjector} bean and uses
 * {@code @TestPropertySource} / {@code @Import} to put its injector ahead of this default;
 * see PATTERNS §"Group 1" for the exact test-time override discipline.
 *
 * <p>No logging on the hot path — the orchestrator calls
 * {@link #maybeFailAt(String, int)} every 50 rows and the production cost MUST be a single
 * virtual dispatch with no allocations.
 */
@Primary
@Component
public class NoopRestoreFailureInjector implements RestoreFailureInjector {

    @Override
    public void maybeFailAt(String tableName, int rowIndex) {
        /* no-op */
    }
}
