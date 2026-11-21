package org.ctc.domain.model;

import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TieBreakRulesTest {
    private final UUID home = UUID.randomUUID();
    private final UUID away = UUID.randomUUID();

    @Test
    void givenParticipantWinnerOfTiedPairingInEnabledPhase_whenValid_thenTrue() {
        assertThat(TieBreakRules.valid(true, home, home, away, 40, 40, false, null)).isTrue();
        assertThat(TieBreakRules.valid(false, null, home, away, 40, 40, false, null)).as("no winner is always valid").isTrue();
    }

    @Test
    void givenBrokenInvariant_whenValid_thenFalse() {
        assertThat(TieBreakRules.valid(false, home, home, away, 40, 40, false, null)).as("phase without rule").isFalse();
        assertThat(TieBreakRules.valid(true, UUID.randomUUID(), home, away, 40, 40, false, null)).as("not a participant").isFalse();
        assertThat(TieBreakRules.valid(true, home, home, away, 41, 40, false, null)).as("not tied").isFalse();
        assertThat(TieBreakRules.valid(true, home, home, away, null, null, false, null)).as("unplayed").isFalse();
        assertThat(TieBreakRules.valid(true, home, home, away, 40, 40, true, null)).as("bye").isFalse();
        assertThat(TieBreakRules.valid(true, home, home, away, 40, 40, false, away)).as("walkover").isFalse();
    }
}
