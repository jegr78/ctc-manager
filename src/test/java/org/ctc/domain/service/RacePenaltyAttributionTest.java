package org.ctc.domain.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.ctc.domain.model.*;
import org.ctc.domain.repository.RaceLineupRepository;
import org.ctc.domain.repository.RaceRepository;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class RacePenaltyAttributionTest {
    @Test
    void givenLegacySubTeamWithoutLineup_whenCalculatingParentRaceScore_thenAttributeBeforePenaltyOffset() {
        // given
        var season = new Season("Test legacy penalty season"); season.setId(UUID.randomUUID());
        var home = new Team("Test parent", "T-PAR"); home.setId(UUID.randomUUID());
        var sub = new Team("Test sub", "T-SUB", home); sub.setId(UUID.randomUUID());
        var away = new Team("Test away", "T-AWY"); away.setId(UUID.randomUUID());
        var day = PhaseTestFixtures.matchdayInRegularPhase(season, "Test round", 1);
        var race = new Race(); race.setId(UUID.randomUUID()); race.setMatchday(day); race.setMatch(new Match(day, home, away));
        var driver = new Driver("TestLegacyPenalty", "Test driver"); driver.setId(UUID.randomUUID());
        driver.getSeasonDrivers().add(new SeasonDriver(season, driver, sub));
        var result = new RaceResult(race, driver, 1, 1, false); result.setPointsTotal(80); race.getResults().add(result);
        var races = mock(RaceRepository.class); when(races.findById(race.getId())).thenReturn(Optional.of(race));
        var lineups = mock(RaceLineupRepository.class); when(lineups.findByRaceId(race.getId())).thenReturn(List.of());
        var scoring = new ScoringService(null, null, lineups, races);
        // when
        var totals = scoring.calculateRaceTotals(race, home.getId());
        // then
        assertThat(totals.home().gross()).isEqualTo(80); assertThat(totals.away().gross()).isZero();
    }
}
