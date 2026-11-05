package org.ctc.domain.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.ctc.domain.model.*;
import org.ctc.domain.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RaceCategorySaveTest {
    @Mock private IncidentLifecycleGuard incidentGuard;
    @Mock private RacePenaltyLifecycleGuard penaltyGuard;
    @Mock RaceRepository raceRepository;
    @Mock MatchRepository matchRepository;
    @Mock MatchdayRepository matchdayRepository;
    @Mock SeasonRepository seasonRepository;
    @Mock TeamRepository teamRepository;
    @Mock CarRepository carRepository;
    @Mock TrackRepository trackRepository;
    @InjectMocks RaceService service;
    Season season;
    Matchday matchday;
    Team home;
    Team away;
    Race race;
    Car mr;
    Car rr;
    SeasonCarPoolEntry entry;

    @BeforeEach
    void setUp() {
        org.springframework.test.util.ReflectionTestUtils.setField(service, "pickGuard",
                new PickLifecycleGuard(org.mockito.Mockito.mock(MatchdayPickRepository.class)));
        org.springframework.test.util.ReflectionTestUtils.setField(service, "reservations", org.mockito.Mockito.mock(PickReservationService.class));
        org.springframework.test.util.ReflectionTestUtils.setField(service, "settingsApplication",
                new RaceSettingsApplicationService(carRepository, trackRepository, null));
        org.springframework.test.util.ReflectionTestUtils.setField(service, "carSelections",
                new RaceCarSelectionService(seasonRepository, teamRepository, raceRepository,
                        org.mockito.Mockito.mock(PickReservationService.class)));

        season = new Season("Test native selection");
        season.setId(UUID.fromString("00000000-0000-0000-0000-000000000800"));
        season.setCarPoolMode(CarPoolMode.CATEGORY_POOL);
        season.setCarPoolState(CarPoolState.FROZEN);
        var phase = new SeasonPhase(season, PhaseType.REGULAR, PhaseLayout.LEAGUE, 0);
        phase.setId(UUID.randomUUID());
        matchday = new Matchday(phase, "Test selection MD", 0);
        matchday.setId(UUID.randomUUID());
        home = team("T-NHOME");
        away = team("T-NAWAY");
        var match = new Match(matchday, home, away);
        match.setId(UUID.randomUUID());
        race = new Race();
        race.setId(UUID.randomUUID());
        race.setMatchday(matchday);
        race.setMatch(match);
        race.setSettings(new RaceSettings(race));
        race.getSettings().setNumberOfLaps(5);
        mr = car("MR");
        rr = car("RR");
        entry = new SeasonCarPoolEntry();
        entry.setId(UUID.randomUUID());
        entry.setSeason(season);
        entry.setLabel("Test MR/RR");
        season.getCarPoolEntries().add(entry);
        for (var car : List.of(mr, rr)) {
            var member = new SeasonCarPoolMember();
            member.setId(UUID.randomUUID());
            member.setEntry(entry);
            member.setCar(car);
            entry.getMembers().add(member);
        }
        lenient().when(seasonRepository.findByIdForUpdate(season.getId())).thenReturn(Optional.of(season));
        lenient().when(matchdayRepository.findById(matchday.getId())).thenReturn(Optional.of(matchday));
        lenient().when(teamRepository.findById(home.getId())).thenReturn(Optional.of(home));
        lenient().when(teamRepository.findById(away.getId())).thenReturn(Optional.of(away));
        lenient().when(raceRepository.findById(race.getId())).thenReturn(Optional.of(race));
        lenient().when(raceRepository.findByIdForSelectionUpdate(race.getId())).thenReturn(Optional.of(race));
        lenient().when(raceRepository.findBySeasonForSelectionUpdate(season.getId())).thenReturn(List.of(race));
        lenient().when(raceRepository.findByMatchId(match.getId())).thenReturn(List.of(race));
        lenient().when(carRepository.findById(mr.getId())).thenReturn(Optional.of(mr));
        lenient().when(carRepository.findById(rr.getId())).thenReturn(Optional.of(rr));
    }

    @Test
    void givenFrozenEntry_whenSavingMember_thenStoreConcreteCarEntryAndSettingsTogether() {
        // given / when
        var result = save(entry.getId(), mr.getId());
        // then
        assertThat(result.success()).isTrue();
        assertThat(race.getCarPoolEntry()).isSameAs(entry);
        assertThat(race.getCar()).isSameAs(mr);
        assertThat(race.getSettings().getNumberOfLaps()).isEqualTo(12);
        verify(raceRepository).save(race);
    }

    @Test
    void givenExistingSelection_whenChangingMember_thenExcludeOwnRace() {
        // given
        race.setCarPoolEntry(entry);
        race.setCar(mr);
        // when
        var result = save(entry.getId(), rr.getId());
        // then
        assertThat(result.success()).isTrue();
        assertThat(race.getCar()).isSameAs(rr);
        assertThat(race.getCarPoolEntry()).isSameAs(entry);
    }

    @Test
    void givenSavedSelection_whenClearingBothNativeFields_thenKeepUnconfiguredDraft() {
        // given
        race.setCarPoolEntry(entry);
        race.setCar(mr);
        // when
        var result = save(null, null);
        // then
        assertThat(result.success()).isTrue();
        assertThat(race.getCar()).isNull();
        assertThat(race.getCarPoolEntry()).isNull();
    }

    @Test
    void givenSavedSelection_whenSubmittingForgedOrIncompletePair_thenPreserveEveryManagedField() {
        // given
        race.setCarPoolEntry(entry);
        race.setCar(mr);
        // when / then
        for (UUID submitted : new UUID[] {UUID.randomUUID(), null}) {
            var result = save(submitted, rr.getId());
            assertThat(result.success()).isFalse();
            assertThat(result.message()).containsIgnoringCase("entry");
            assertThat(race.getCar()).isSameAs(mr);
            assertThat(race.getCarPoolEntry()).isSameAs(entry);
            assertThat(race.getHomeTeam()).isSameAs(home);
            assertThat(race.getMatchday()).isSameAs(matchday);
            assertThat(race.getSettings().getNumberOfLaps()).isEqualTo(5);
        }
        verify(raceRepository, never()).save(any());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {true, false})
    void givenCrossSeasonMove_whenSaving_thenAcquireBothSeasonLocksInUuidOrder(boolean targetBeforeSource) {
        // given
        var target = new Season("Test ordered target");
        target.setId(UUID.fromString(targetBeforeSource ? "00000000-0000-0000-0000-000000000400"
                : "00000000-0000-0000-0000-000000000c00"));
        target.setCarPoolMode(CarPoolMode.CATEGORY_POOL);
        target.setCarPoolState(CarPoolState.FROZEN);
        var targetPhase = new SeasonPhase(target, PhaseType.REGULAR, PhaseLayout.LEAGUE, 0);
        targetPhase.setId(UUID.randomUUID());
        var targetMatchday = new Matchday(targetPhase, "Test target matchday", 0);
        targetMatchday.setId(UUID.randomUUID());
        var targetEntry = new SeasonCarPoolEntry();
        targetEntry.setId(UUID.randomUUID());
        targetEntry.setSeason(target);
        targetEntry.setLabel("Test target entry");
        var member = new SeasonCarPoolMember();
        member.setEntry(targetEntry);
        member.setCar(mr);
        targetEntry.getMembers().add(member);
        target.getCarPoolEntries().add(targetEntry);
        org.mockito.Mockito.when(seasonRepository.findByIdForUpdate(target.getId())).thenReturn(Optional.of(target));
        org.mockito.Mockito.when(matchdayRepository.findById(targetMatchday.getId())).thenReturn(Optional.of(targetMatchday));
        // when
        var result = service.saveRace(race.getId(), targetMatchday.getId(), home.getId(), away.getId(), null, mr.getId(), null,
                12, null, null, null, null, null, TimeProgressionMode.MULTIPLIER, 0, null, null, null, null, targetEntry.getId());
        // then
        assertThat(result.success()).isTrue();
        var firstLocks = org.mockito.Mockito.mockingDetails(seasonRepository).getInvocations().stream()
                .filter(invocation -> invocation.getMethod().getName().equals("findByIdForUpdate"))
                .limit(2).map(invocation -> invocation.<UUID>getArgument(0)).toList();
        assertThat(firstLocks).containsExactly(targetBeforeSource ? target.getId() : season.getId(),
                targetBeforeSource ? season.getId() : target.getId());
    }

    @Test
    void givenRaceMovesWhileWaitingForLock_whenSavingCachedContext_thenRejectWithoutChangingLatestState() {
        // given
        var actualSeason = new Season("Test concurrent owner");
        actualSeason.setId(UUID.randomUUID());
        var phase = new SeasonPhase(actualSeason, PhaseType.REGULAR, PhaseLayout.LEAGUE, 0);
        phase.setId(UUID.randomUUID());
        var currentMatchday = new Matchday(phase, "Test concurrent destination", 0);
        currentMatchday.setId(UUID.randomUUID());
        org.mockito.Mockito.when(seasonRepository.findByIdForUpdate(season.getId())).thenAnswer(inv -> {
            race.setMatchday(currentMatchday);
            race.getMatch().setMatchday(currentMatchday);
            return Optional.of(season);
        });
        // when / then
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> save(entry.getId(), mr.getId()))
                .isInstanceOf(org.ctc.domain.exception.BusinessRuleException.class).hasMessageContaining("moved while saving");
        assertThat(race.getMatchday()).isSameAs(currentMatchday);
        assertThat(race.getCar()).isNull();
        assertThat(race.getSettings().getNumberOfLaps()).isEqualTo(5);
        verify(raceRepository, never()).save(any());
    }

    private RaceService.SaveResult save(UUID entryId, UUID carId) {
        return service.saveRace(race.getId(), matchday.getId(), home.getId(), away.getId(), null, carId, null,
                12, null, null, null, null, null, TimeProgressionMode.MULTIPLIER, 0,
                null, null, null, null, entryId);
    }

    private static Team team(String name) {
        var team = new Team("Test native " + name, name);
        team.setId(UUID.randomUUID());
        return team;
    }

    private static Car car(String name) {
        var car = new Car("Test native", name);
        car.setId(UUID.randomUUID());
        return car;
    }
}
