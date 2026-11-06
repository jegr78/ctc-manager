package org.ctc.domain.service;

import java.util.*;
import java.util.concurrent.*;
import org.ctc.domain.model.*;
import org.ctc.domain.repository.RaceDisqualificationRevisionRepository;
import org.ctc.domain.repository.RaceRepository;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DisqualificationViewTest {
    @Test
    void givenRestoreDuringViewLoad_whenReturningDsqForm_thenKeepGenerationCapturedBeforeDataRead() throws Exception {
        // given
        var epoch = new PickSessionEpoch(); String original = epoch.managementGeneration();
        var races = mock(RaceRepository.class); var scoring = mock(ScoringService.class);
        var service = new RaceDisqualificationService(races, mock(RaceDisqualificationRevisionRepository.class), scoring, epoch,
                mock(PickWriteExecutor.class), mock(ScoreWriteLockService.class));
        var home = new Team(); home.setId(UUID.randomUUID()); var away = new Team(); away.setId(UUID.randomUUID());
        var race = new Race(); race.setId(UUID.randomUUID()); race.setHomeTeamOverride(home); race.setAwayTeamOverride(away);
        var driver = new Driver(); driver.setId(UUID.randomUUID()); var result = new RaceResult(race, driver, 1, 1, false);
        result.setId(UUID.randomUUID()); race.getResults().add(result);
        var reading = new CountDownLatch(1); var released = new CountDownLatch(1);
        when(races.findById(race.getId())).thenAnswer(call -> { reading.countDown(); assertThat(released.await(5, TimeUnit.SECONDS)).isTrue(); return Optional.of(race); });
        when(scoring.calculateRaceScoreSnapshot(race, home.getId())).thenReturn(new ScoringService.RaceScoreSnapshot(
                RacePenaltyTotals.calculate(80, 0, 0, 0), List.of(new ScoringService.ScoredDriver(result.getId(), driver.getId(), 1, 1,
                false, 80, 0, 0, 80, true, false))));
        try (var executor = Executors.newSingleThreadExecutor()) {
            // when
            var pending = executor.submit(() -> service.view(race.getId(), result.getId()));
            try { assertThat(reading.await(5, TimeUnit.SECONDS)).isTrue(); epoch.afterRestore(null); }
            finally { released.countDown(); }
            // then
            assertThat(pending.get(5, TimeUnit.SECONDS).generation()).isEqualTo(original).isNotEqualTo(epoch.managementGeneration());
        }
    }
}
