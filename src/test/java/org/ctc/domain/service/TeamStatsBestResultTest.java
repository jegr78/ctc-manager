package org.ctc.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TeamStatsBestResultTest {

    @Test
    void givenOneOrSeveralSeasonsWithTheBestRank_whenFormatting_thenNameTheSeasonOrCountThemWithTheLatest() {
        // when / then
        assertThat(TeamStatsService.bestResult(0, 0, null)).isNull();
        assertThat(TeamStatsService.bestResult(1, 1, "Test CTC Archive 2025")).isEqualTo("1st in Test CTC Archive 2025");
        assertThat(TeamStatsService.bestResult(1, 2, "Test CTC Archive 2026")).as("a shared best rank counts the seasons and names the latest")
                .isEqualTo("1st (2×, latest Test CTC Archive 2026)");
    }
}
