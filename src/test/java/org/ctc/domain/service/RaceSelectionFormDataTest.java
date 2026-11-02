package org.ctc.domain.service;

import java.util.List;
import java.util.Optional;
import java.util.Set;
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
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RaceSelectionFormDataTest {
    @Mock private PickReservationService reservations;
    @Mock RaceRepository raceRepository;
    @Mock MatchdayRepository matchdayRepository;
    @Mock TeamRepository teamRepository;
    @Mock CarRepository carRepository;
    @Mock TrackRepository trackRepository;
    @Mock SeasonDriverRepository seasonDriverRepository;
    @Mock RaceLineupRepository raceLineupRepository;
    @Mock SeasonRepository seasonRepository;
    @Mock RaceCarSelectionService carSelections;
    @InjectMocks RaceFormDataService forms;
    Season season;
    Matchday matchday;
    Team home;
    Race race;
    Car car;
    SeasonCarPoolEntry entry;
    RaceCarSelectionService.EntryChoice choice;

    @BeforeEach
    void setUp() {
        season = new Season("Test selection form");
        season.setId(UUID.randomUUID());
        season.setCarPoolMode(CarPoolMode.CATEGORY_POOL);
        season.setCarPoolState(CarPoolState.FROZEN);
        matchday = PhaseTestFixtures.matchdayInRegularPhase(season, "Test category MD", 0);
        matchday.setId(UUID.randomUUID());
        home = new Team("Test selection home", "T-FORM");
        home.setId(UUID.randomUUID());
        race = new Race();
        race.setId(UUID.randomUUID());
        race.setMatchday(matchday);
        race.setMatch(new Match(matchday, home, null));
        car = new Car("Test form", "MR");
        car.setId(UUID.randomUUID());
        entry = new SeasonCarPoolEntry();
        entry.setId(UUID.randomUUID());
        entry.setSeason(season);
        entry.setLabel("Test frozen MR/RR");
        race.setCar(car);
        race.setCarPoolEntry(entry);
        choice = new RaceCarSelectionService.EntryChoice(entry.getId(), entry.getLabel(), List.of(
                new RaceCarSelectionService.CarChoice(car.getId(), car.getDisplayName())), false, null);
        lenient().when(raceRepository.findById(race.getId())).thenReturn(Optional.of(race));
        lenient().when(matchdayRepository.findById(matchday.getId())).thenReturn(Optional.of(matchday));
        lenient().when(seasonRepository.findById(season.getId())).thenReturn(Optional.of(season));
        lenient().when(teamRepository.findAll()).thenReturn(List.of(home));
        lenient().when(carRepository.findById(car.getId())).thenReturn(Optional.of(car));
        lenient().when(carSelections.availability(any(), any(), any())).thenReturn(new RaceCarSelectionService.Availability(
                CarPoolMode.CATEGORY_POOL, CarPoolState.FROZEN, List.of(choice), Set.of(), Set.of()));
    }

    @Test
    void givenStoredEntry_whenEditingOrOpeningResults_thenRetainEntryIdAndFrozenMemberChoices() {
        // given / when
        var form = forms.getRaceFormData(race.getId());
        var results = forms.getResultsFormData(race.getId());
        // then
        assertThat(form.data().carPoolEntryId()).isEqualTo(entry.getId());
        assertThat(results.data().carPoolEntryId()).isEqualTo(entry.getId());
        assertThat(form.carSelection().category()).isTrue();
        assertThat(form.carSelection().frozen()).isTrue();
        assertThat(form.carSelection().entries()).containsExactly(choice);
        assertThat(form.carSelection().cars()).extracting(RaceCarSelectionService.CarChoice::id).containsExactly(car.getId());
        assertThat(form.outOfPoolCar()).isNull();
    }

    @Test
    void givenSubmittedContext_whenRebuildingChoices_thenUseSubmittedHomeEntryAndOwnRaceExclusion() {
        // given
        UUID submittedHome = UUID.randomUUID();
        var submitted = new Team("Test submitted context", "T-SUBMIT");
        submitted.setId(submittedHome);
        org.mockito.Mockito.when(teamRepository.findAll()).thenReturn(List.of(home, submitted));
        // when
        var form = forms.getSubmittedRaceFormData(matchday.getId(), submittedHome, race.getId(), entry.getId(), car.getId(), null);
        // then
        verify(carSelections).availability(season.getId(), submittedHome, race.getId());
        assertThat(form.data().id()).isEqualTo(race.getId());
        assertThat(form.data().homeTeamId()).isEqualTo(submittedHome);
        assertThat(form.data().carPoolEntryId()).isEqualTo(entry.getId());
        assertThat(form.data().carId()).isEqualTo(car.getId());
        assertThat(form.carSelection().cars()).hasSize(1);
    }

    @Test
    void givenUnavailableSubmittedIds_whenRebuildingChoices_thenKeepTheValuesAndVisibleUnavailableOptions() {
        // given
        UUID foreignEntry = UUID.randomUUID();
        UUID unknownCar = UUID.randomUUID();
        // when
        var form = forms.getSubmittedRaceFormData(matchday.getId(), home.getId(), race.getId(), foreignEntry, unknownCar, null);
        // then
        assertThat(form.data().carPoolEntryId()).isEqualTo(foreignEntry);
        assertThat(form.data().carId()).isEqualTo(unknownCar);
        assertThat(form.carSelection().unavailableEntry().id()).isEqualTo(foreignEntry);
        assertThat(form.carSelection().unavailableCar().id()).isEqualTo(unknownCar);
        assertThat(form.carSelection().cars()).isEmpty();
    }

    @Test
    void givenCategoryAvailability_whenReadingUsedSelections_thenRetainOldKeysAndAddEntryChoicesAndConsumedIds() {
        // given / when
        var data = forms.getUsedSelections(season.getId(), home.getId(), race.getId());
        // then
        assertThat(data).containsKeys("usedCarIds", "usedTrackIds", "entries", "consumedEntryIds", "carPoolMode", "carPoolState");
        assertThat(data.get("entries")).isEqualTo(List.of(choice));
    }
}
