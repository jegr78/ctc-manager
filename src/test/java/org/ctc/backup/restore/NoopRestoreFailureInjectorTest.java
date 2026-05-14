package org.ctc.backup.restore;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Primary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

/**
 * Unit test for {@link NoopRestoreFailureInjector} — guards the production-default contract
 * declared in CONTEXT D-13:
 *
 * <ul>
 *   <li>Method body MUST be a no-op (no exception, no side effect).</li>
 *   <li>Class MUST carry the {@link Primary} annotation so that the test-scope
 *       {@code FailAtTableInjector} in {@code BackupImportRollbackIT} (Plan 08) can override
 *       it predictably.</li>
 * </ul>
 */
class NoopRestoreFailureInjectorTest {

    private final NoopRestoreFailureInjector noop = new NoopRestoreFailureInjector();

    @Test
    void whenMaybeFailAtCalled_thenNoExceptionAndNoSideEffect() {
        // given / when / then
        assertThatNoException()
                .isThrownBy(() -> noop.maybeFailAt("anything", 12345));
    }

    @Test
    void givenClass_whenInspected_thenCarriesPrimaryAnnotation() {
        // given / when
        boolean hasPrimary = NoopRestoreFailureInjector.class.isAnnotationPresent(Primary.class);

        // then
        assertThat(hasPrimary)
                .as("NoopRestoreFailureInjector must be @Primary so test-scope overrides can replace it")
                .isTrue();
    }
}
