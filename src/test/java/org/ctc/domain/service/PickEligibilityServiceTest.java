package org.ctc.domain.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.model.*;
import org.ctc.domain.repository.RaceRepository;
import org.ctc.domain.repository.TrackRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PickEligibilityServiceTest {
    final RaceCarSelectionService cars = mock(RaceCarSelectionService.class);
    final RaceRepository races = mock(RaceRepository.class);
    final TrackRepository tracks = mock(TrackRepository.class);
    final PickReservationService reservations = mock(PickReservationService.class);
    final PickEligibilityService service = new PickEligibilityService(cars, races, tracks, reservations);
    final Season season = new Season("Test_PickEligibility");
    final Race race = new Race();
    final Team home = new Team();
    final Team other = new Team();
    final PickPayload payload = PickSubmissionServiceTest.payload();
    final Track track = new Track();

    @BeforeEach
    void setUp() {
        season.setId(UUID.randomUUID());
        race.setId(UUID.randomUUID());
        home.setId(UUID.randomUUID());
        other.setId(UUID.randomUUID());
        race.setHomeTeamOverride(home);
        var phase = new SeasonPhase();
        phase.setSeason(season);
        race.setMatchday(new Matchday(phase, "Test eligibility", 1));
        track.setId(payload.trackId());
        season.getTracks().add(track);
        when(tracks.findById(payload.trackId())).thenReturn(Optional.of(track));
        when(cars.validate(any(), any(), any(), any(), any())).thenReturn(new RaceCarSelectionService.Validation(null, null, new Car()));
        when(races.findBySeasonForSelectionUpdate(season.getId())).thenReturn(List.of());
        when(reservations.pending(season.getId(), true)).thenReturn(List.of());
    }

    @Test
    void givenValidPayload_whenChecking_thenReuseNativeCarSelectionAndAcceptAvailableTrack() {
        assertThatCode(() -> service.validate(race, payload)).doesNotThrowAnyException();
        verify(cars).validate(season.getId(), home.getId(), race.getId(), payload.carPoolEntryId(), payload.carId());
    }

    @Test
    void givenSharedCategoryRejection_whenChecking_thenKeepItsReason() {
        when(cars.validate(any(), any(), any(), any(), any())).thenReturn(new RaceCarSelectionService.Validation("Choose a frozen member", null, null));
        assertThatThrownBy(() -> service.validate(race, payload)).isInstanceOf(BusinessRuleException.class).hasMessage("Choose a frozen member");
    }

    @Test
    void givenTrackOutsideSeason_whenChecking_thenReject() {
        season.getTracks().clear();
        assertThatThrownBy(() -> service.validate(race, payload)).isInstanceOf(BusinessRuleException.class).hasMessageContaining("season");
    }

    @Test
    void givenExistingTrackUsageBySameHome_whenChecking_thenReject() {
        when(races.findBySeasonForSelectionUpdate(season.getId())).thenReturn(List.of(used(home, UUID.randomUUID())));
        assertThatThrownBy(() -> service.validate(race, payload)).isInstanceOf(BusinessRuleException.class).hasMessageContaining("track");
    }

    @Test
    void givenCurrentTargetOrOtherHomeTrackUsage_whenChecking_thenExcludeCurrentTargetOnce() {
        when(races.findBySeasonForSelectionUpdate(season.getId())).thenReturn(List.of(used(home, race.getId()), used(other, UUID.randomUUID())));
        assertThatCode(() -> service.validate(race, payload)).doesNotThrowAnyException();
    }

    @Test
    void givenPendingTrackReservationBySameHome_whenChecking_thenRejectEvenWithoutSavedRaceTrack() {
        when(reservations.pending(season.getId(), true)).thenReturn(List.of(
                new PickReservationService.Reservation(UUID.randomUUID(), home.getId(), payload.carId(), null, payload.trackId())));
        assertThatThrownBy(() -> service.validate(race, payload)).isInstanceOf(BusinessRuleException.class).hasMessageContaining("reserved");
    }

    @Test
    void givenOwnPendingReservationAndOtherHomeReservation_whenReplacing_thenExcludeCurrentTargetOnce() {
        when(reservations.pending(season.getId(), true)).thenReturn(List.of(
                new PickReservationService.Reservation(race.getId(), home.getId(), payload.carId(), null, payload.trackId()),
                new PickReservationService.Reservation(UUID.randomUUID(), other.getId(), payload.carId(), null, payload.trackId())));
        assertThatCode(() -> service.validate(race, payload)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"regular", "playoff"})
    void givenByeWithHistoricalTrack_whenChecking_thenDoNotConsumeTrackForARealRace(String kind) {
        // given
        var bye = used(home, UUID.randomUUID());
        if (kind.equals("regular")) {
            var match = new Match();
            match.setBye(true);
            bye.setMatch(match);
        } else {
            var matchup = new PlayoffMatchup();
            matchup.setBye(true);
            bye.setPlayoffMatchup(matchup);
        }
        when(races.findBySeasonForSelectionUpdate(season.getId())).thenReturn(List.of(bye));
        // when / then
        assertThatCode(() -> service.validate(race, payload)).doesNotThrowAnyException();
    }

    private Race used(Team team, UUID id) {
        var used = new Race();
        used.setId(id);
        used.setHomeTeamOverride(team);
        used.setTrack(track);
        return used;
    }
}
