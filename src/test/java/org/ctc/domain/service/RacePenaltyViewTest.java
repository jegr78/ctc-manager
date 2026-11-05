package org.ctc.domain.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.ctc.domain.model.Race;
import org.ctc.domain.model.RacePenaltyTotals;
import org.ctc.domain.model.Team;
import org.ctc.domain.repository.RacePenaltyRevisionRepository;
import org.ctc.domain.repository.RaceRepository;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class RacePenaltyViewTest {
    @Test
    void givenViewOverlappingRestoreGeneration_whenReturningForm_thenCannotStampOldDataWithNewGeneration() throws Exception {
        // given
        var epoch = new PickSessionEpoch(); String original = epoch.managementGeneration();
        var races = mock(RaceRepository.class); var scoring = mock(ScoringService.class);
        var revisions = mock(RacePenaltyRevisionRepository.class);
        var service = new RacePenaltyService(races, scoring, epoch, mock(PickWriteExecutor.class), mock(ScoreWriteLockService.class), revisions);
        var race = new Race(); race.setId(UUID.randomUUID());
        var home = new Team("Test home", "T-HOM"); home.setId(UUID.randomUUID());
        var away = new Team("Test away", "T-AWY"); away.setId(UUID.randomUUID());
        race.setHomeTeamOverride(home); race.setAwayTeamOverride(away);
        var reading = new CountDownLatch(1); var released = new CountDownLatch(1);
        when(races.findById(race.getId())).thenAnswer(call -> {
            reading.countDown(); assertThat(released.await(5, TimeUnit.SECONDS)).isTrue(); return Optional.of(race);
        });
        when(scoring.calculateRaceScoreSnapshot(race, home.getId())).thenReturn(
                new ScoringService.RaceScoreSnapshot(RacePenaltyTotals.calculate(80, 70, 20, 10), List.of()));
        try (var executor = Executors.newSingleThreadExecutor()) {
            // when
            var pending = executor.submit(() -> service.view(race.getId()));
            try {
                assertThat(reading.await(5, TimeUnit.SECONDS)).isTrue(); epoch.afterRestore(null);
            } finally { released.countDown(); }
            // then
            assertThat(pending.get(5, TimeUnit.SECONDS).generation()).isEqualTo(original).isNotEqualTo(epoch.managementGeneration());
        }
    }
}
