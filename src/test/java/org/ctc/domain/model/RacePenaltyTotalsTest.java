package org.ctc.domain.model;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class RacePenaltyTotalsTest {
    @ParameterizedTest
    @CsvSource({
            "-5,-8,0,0,0,0", "-5,70,5,5,0,70",
            "80,70,0,0,80,70", "80,70,20,10,70,70", "80,70,15,15,80,70",
            "80,70,100,110,80,60", "80,70,100,0,0,70", "80,70,0,100,80,0",
            "2147483647,2147483647,2147483647,0,0,2147483647",
            "2147483647,2147483647,2147483647,2147483647,2147483647,2147483647"})
    void givenTeamScoresAndPenalties_whenOffsetting_thenApplyDifferenceAndFloor(
            int homeGross, int awayGross, int homePenalty, int awayPenalty, int homeOfficial, int awayOfficial) {
        // when
        var totals = RacePenaltyTotals.calculate(homeGross, awayGross, homePenalty, awayPenalty);
        // then
        assertThat(totals.home().official()).isEqualTo(homeOfficial);
        assertThat(totals.away().official()).isEqualTo(awayOfficial);
        assertThat(totals.home().gross()).isEqualTo(homeGross);
        assertThat(totals.away().gross()).isEqualTo(awayGross);
        assertThat(totals.home().penalty()).isEqualTo(homePenalty);
        assertThat(totals.away().penalty()).isEqualTo(awayPenalty);
        assertThat(totals.home().deduction()).isEqualTo(Math.max((long) homePenalty - awayPenalty, 0));
        assertThat(totals.away().deduction()).isEqualTo(Math.max((long) awayPenalty - homePenalty, 0));
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, Integer.MIN_VALUE})
    void givenNegativePenalty_whenCalculating_thenRejectEitherTeam(int negative) {
        // when / then
        assertThatThrownBy(() -> RacePenaltyTotals.calculate(80, 70, negative, 0))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("nonnegative");
        assertThatThrownBy(() -> RacePenaltyTotals.calculate(80, 70, 0, negative))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("nonnegative");
    }
}
