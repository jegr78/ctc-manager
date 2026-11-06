package org.ctc.domain.service;

import java.util.*;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.model.*;
import org.ctc.domain.repository.RaceDisqualificationRevisionRepository;
import org.ctc.domain.repository.RaceResultRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DisqualificationLifecycleGuardTest {
    private final RaceDisqualificationRevisionRepository revisions = mock(RaceDisqualificationRevisionRepository.class);
    private final RaceResultRepository results = mock(RaceResultRepository.class);
    private final RaceDisqualificationLifecycleGuard guard = new RaceDisqualificationLifecycleGuard(revisions, results);
    private final Team home = team();
    private final Team away = team();

    private Team team() { var team = new Team(); team.setId(UUID.randomUUID()); return team; }
    private Race race() {
        var race = new Race(); race.setId(UUID.randomUUID()); var day = new Matchday(); day.setId(UUID.randomUUID());
        race.setMatchday(day); race.setHomeTeamOverride(home); race.setAwayTeamOverride(away); return race;
    }

    @Test
    void givenUnsavedDraft_whenCheckingHistory_thenDoNotIssueLockingQueries() {
        // when / then
        assertThat(guard.protects(new Race())).isFalse(); verifyNoInteractions(revisions, results);
    }

    @ParameterizedTest
    @ValueSource(strings = {"day", "home", "away"})
    void givenPermanentHistory_whenChangingIdentity_thenRejectButAllowSameIdentity(String kind) {
        // given
        var race = race(); var row = mock(RaceDisqualificationRevision.class);
        when(revisions.findRaceHistoryForUpdate(race.getId())).thenReturn(List.of(row));
        var day = kind.equals("day") ? UUID.randomUUID() : race.getMatchday().getId();
        var nextHome = kind.equals("home") ? away : home; var nextAway = kind.equals("away") ? home : away;
        // when / then
        assertThatThrownBy(() -> guard.requireIdentity(race, day, nextHome, nextAway)).isInstanceOf(BusinessRuleException.class).hasMessageContaining("DSQ");
        assertThatCode(() -> guard.requireIdentity(race, race.getMatchday().getId(), home, away)).doesNotThrowAnyException();
        assertThatThrownBy(() -> guard.requireNoHistory(List.of(race))).isInstanceOf(BusinessRuleException.class).hasMessageContaining("DSQ");
    }

    @Test
    void givenCurrentHistoryButEmptyCachedCollection_whenDroppingItsDriver_thenUseNativeOwnershipBeforeMutation() {
        // given
        var race = race(); var driver = new Driver(); driver.setId(UUID.randomUUID());
        var row = mock(RaceDisqualificationRevision.class); when(row.getDriver()).thenReturn(driver);
        when(revisions.findRaceHistoryForUpdate(race.getId())).thenReturn(List.of(row));
        // when / then
        assertThatThrownBy(() -> guard.requireRetainedDrivers(race, Set.of())).isInstanceOf(BusinessRuleException.class).hasMessageContaining("permanent DSQ history");
        assertThatCode(() -> guard.requireRetainedDrivers(race, Set.of(driver.getId()))).doesNotThrowAnyException();
    }

    @Test
    void givenFlagWithoutHistory_whenRemovingResult_thenPreserveStateForManagementReview() {
        // given
        var race = race(); var driver = new Driver(); driver.setId(UUID.randomUUID());
        var result = new RaceResult(race, driver, 1, 1, false); result.setDisqualified(true); race.getResults().add(result);
        // when / then
        assertThat(guard.protects(race)).isTrue();
        assertThatThrownBy(() -> guard.requireRetainedDrivers(race, Set.of())).isInstanceOf(BusinessRuleException.class).hasMessageContaining("DSQ state");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void givenPlayoffHistoryOrCurrentDsqFlag_whenChangingParentParticipants_thenRejectIndirectIdentityChange(boolean flagOnly) {
        // given
        var matchup = new PlayoffMatchup(); matchup.setId(UUID.randomUUID()); matchup.setTeam1(home); matchup.setTeam2(away);
        if (flagOnly) when(results.findDisqualifiedPlayoffResultsForUpdate(matchup.getId())).thenReturn(List.of(new RaceResult()));
        else when(revisions.findPlayoffHistoryForUpdate(matchup.getId())).thenReturn(List.of(mock(RaceDisqualificationRevision.class)));
        // when / then
        assertThatThrownBy(() -> guard.requirePlayoffIdentity(matchup, away, home)).isInstanceOf(BusinessRuleException.class).hasMessageContaining("DSQ");
        assertThatCode(() -> guard.requirePlayoffIdentity(matchup, home, away)).doesNotThrowAnyException();
    }
}
