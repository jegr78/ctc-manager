package org.ctc.backup.service;

import org.ctc.backup.exception.RestoreFailureSimulatedException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Phase 75 / Plan 09 — unit test for {@link FailAtTableInjector}.
 *
 * <p>The {@link org.ctc.backup.restore.RestoreFailureInjector} test seam (D-13) is exercised
 * here without {@code @SpringBootTest}: each test instantiates the injector directly with a
 * concrete {@code (targetTable, targetRow)} pair and invokes
 * {@link FailAtTableInjector#maybeFailAt(String, int)} with various input combinations.
 *
 * <p>The {@code FailAtTableInjector.Config} {@code @TestConfiguration} nested class is covered
 * by {@code BackupImportRollbackIT} where the bean is wired into a real Spring context.
 */
class FailAtTableInjectorTest {

    @Test
    void givenMatchingTargetTableAndRow_whenMaybeFailAtCalled_thenThrowsSimulatedException() {
        // given
        FailAtTableInjector injector = new FailAtTableInjector("race_results", 500);

        // when / then
        assertThatThrownBy(() -> injector.maybeFailAt("race_results", 500))
                .isInstanceOf(RestoreFailureSimulatedException.class)
                .hasMessageContaining("race_results")
                .hasMessageContaining("500");
    }

    @Test
    void givenNonMatchingTable_whenMaybeFailAtCalled_thenNoException() {
        // given
        FailAtTableInjector injector = new FailAtTableInjector("race_results", 500);

        // when / then
        assertThatNoException()
                .isThrownBy(() -> injector.maybeFailAt("seasons", 500));
    }

    @Test
    void givenMatchingTableButDifferentRow_whenMaybeFailAtCalled_thenNoException() {
        // given
        FailAtTableInjector injector = new FailAtTableInjector("race_results", 500);

        // when / then
        assertThatNoException()
                .isThrownBy(() -> injector.maybeFailAt("race_results", 450));
    }
}
