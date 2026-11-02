package org.ctc.domain.service;

import java.sql.SQLException;
import org.ctc.domain.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.PessimisticLockingFailureException;
import static org.assertj.core.api.Assertions.assertThat;

class RaceSelectionConcurrencyTest {
    @Test
    void givenPessimisticLockConflict_whenTranslating_thenReturnDomainRetryWithOriginalCause() {
        // given
        var failure = new PessimisticLockingFailureException("Test row lock");
        // when
        var result = RaceSelectionConcurrency.translate(failure);
        // then
        assertThat(result).isInstanceOf(BusinessRuleException.class).hasMessageContaining("Reload").hasCause(failure);
    }

    @Test
    void givenMariaDbSnapshotConflict_whenTranslating_thenRetainDatabaseCauseAndOfferRetry() {
        // given
        var failure = new RuntimeException("Test database wrapper", new SQLException("Record changed", "HY000", 1020));
        // when
        var result = RaceSelectionConcurrency.translate(failure);
        // then
        assertThat(result).isInstanceOf(BusinessRuleException.class).hasMessageContaining("changed").hasCause(failure);
    }

    @Test
    void givenUnrelatedDatabaseOrApplicationFailure_whenTranslating_thenDoNotHideItAsSelectionConflict() {
        // given / when / then
        for (var failure : new RuntimeException[] {
                new IllegalStateException("Test application failure"),
                new RuntimeException(new SQLException("Different SQL state", "23000", 1020)),
                new RuntimeException(new SQLException("Different SQL error", "HY000", 1062))}) {
            assertThat(RaceSelectionConcurrency.translate(failure)).isSameAs(failure);
        }
    }
}
