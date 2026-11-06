package org.ctc.domain.service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Supplier;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.model.*;
import org.ctc.domain.repository.RaceDisqualificationRevisionRepository;
import org.ctc.domain.repository.RaceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DisqualificationServiceGuardTest {
    private final RaceRepository races = mock(RaceRepository.class);
    private final RaceDisqualificationRevisionRepository revisions = mock(RaceDisqualificationRevisionRepository.class);
    private final ScoringService scoring = mock(ScoringService.class);
    private final PickSessionEpoch epoch = new PickSessionEpoch();
    private final PickWriteExecutor writes = mock(PickWriteExecutor.class);
    private final RaceDisqualificationService service = new RaceDisqualificationService(races, revisions, scoring, epoch, writes, mock(ScoreWriteLockService.class));

    private RaceResult fixture() {
        var season = new Season(); season.setId(UUID.randomUUID()); var phase = new SeasonPhase(); phase.setId(UUID.randomUUID()); phase.setSeason(season);
        var scheme = new RaceScoring(); scheme.setRacePoints("20,17"); scheme.setQualiPoints(""); phase.setRaceScoring(scheme);
        var day = new Matchday(); day.setId(UUID.randomUUID()); day.setPhase(phase);
        var home = new Team(); home.setId(UUID.randomUUID()); var away = new Team(); away.setId(UUID.randomUUID());
        var match = new Match(); match.setId(UUID.randomUUID()); match.setMatchday(day); match.setHomeTeam(home); match.setAwayTeam(away);
        var race = new Race(); race.setId(UUID.randomUUID()); race.setMatchday(day); race.setMatch(match);
        var driver = new Driver(); driver.setId(UUID.randomUUID()); var result = new RaceResult(race, driver, 1, 1, false);
        result.setId(UUID.randomUUID()); result.setDisqualified(true); race.getResults().add(result);
        when(races.findById(race.getId())).thenReturn(Optional.of(race));
        when(writes.execute(any())).thenAnswer(call -> ((Supplier<?>) call.getArgument(0)).get());
        when(scoring.calculateRaceScoreSnapshot(race, home.getId())).thenReturn(new ScoringService.RaceScoreSnapshot(
                RacePenaltyTotals.calculate(0, 0, 0, 0), List.of(new ScoringService.ScoredDriver(result.getId(), driver.getId(), 1, 1, false, 0, 0, 0, 0, true, true))));
        return result;
    }

    private RaceDisqualificationRevision row(RaceResult result, int revision) {
        var row = new RaceDisqualificationRevision(result, revision, revision > 1, true, "Test reason correction", "test-manager", "a".repeat(64));
        ReflectionTestUtils.setField(row, "id", new UUID(0, revision));
        var time = LocalDateTime.parse("2026-11-05T12:00:00.123456"); row.setCreatedAt(time); row.setUpdatedAt(time); return row;
    }

    private RaceDisqualificationService.Correction command(RaceResult result, int revision) {
        var view = service.view(result.getRace().getId(), result.getId());
        return new RaceDisqualificationService.Correction(true, revision, view.fingerprint(), view.generation(), "Test reason correction");
    }

    @Test
    void givenHistoryExactlyAtBudget_whenAppendingReasonCorrection_thenRejectBeforeSavingAnotherRevision() {
        // given
        var result = fixture(); var history = new ArrayList<RaceDisqualificationRevision>();
        for (int revision = 1; revision <= RaceDisqualificationHistory.MAX_REVISIONS; revision++) history.add(row(result, revision));
        result.getDisqualificationRevisions().addAll(history); when(revisions.findResultHistoryForUpdate(result.getId())).thenReturn(history);
        when(revisions.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        var command = command(result, history.size());
        // when / then
        assertThatThrownBy(() -> service.save(result.getRace().getId(), result.getId(), command, "test-manager"))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("revision budget");
        verify(revisions, never()).saveAndFlush(any()); assertThat(result.getDisqualificationRevisions()).hasSize(RaceDisqualificationHistory.MAX_REVISIONS);
    }

    @Test
    void givenCurrentNativeHistoryDiffersFromLoadedCollection_whenSubmittingItsRevision_thenRejectBeforeChangingState() {
        // given
        var result = fixture(); when(revisions.findResultHistoryForUpdate(result.getId())).thenReturn(List.of(row(result, 1)));
        var command = command(result, 1);
        // when / then
        assertThatThrownBy(() -> service.save(result.getRace().getId(), result.getId(), command, "test-manager"))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("DSQ history changed");
        verify(revisions, never()).saveAndFlush(any()); assertThat(result.isDisqualified()).isTrue();
    }
}
