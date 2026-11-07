package org.ctc.domain.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.ctc.domain.model.*;
import org.ctc.domain.picks.*;
import org.ctc.domain.repository.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class PickTeamReadServiceTest {
    @ParameterizedTest
    @CsvSource({
            "2026-11-08T20:30, 2026-11-08T19:30:00Z",
            "2027-07-01T20:30, 2027-07-01T18:30:00Z",
            "2027-03-28T02:30,",
            "2027-10-31T02:30,",
            ","
    })
    void givenEffectiveBerlinDeadline_whenLoadingPrivateView_thenExposeOnlyUnambiguousInstant(String local, String expected) {
        // given
        var season = new Season("Test season"); season.setId(UUID.randomUUID());
        var phase = new SeasonPhase(); phase.setSeason(season);
        var day = new Matchday(phase, "Test day", 1);
        day.setPickDeadline(local == null ? null : LocalDateTime.parse(local));
        var race = new Race(); race.setId(UUID.randomUUID()); race.setMatchday(day);
        var channel = new ChannelPickActor("100", "200", "300", Set.of());
        var actor = new AuthorizedPickActor(race.getId(), season.getId(), UUID.randomUUID(), "300");
        var access = mock(PickAccessService.class); var targets = mock(PickTargetService.class);
        var races = mock(RaceRepository.class); var picks = mock(MatchdayPickRepository.class);
        var selections = mock(RaceCarSelectionService.class); var reservations = mock(PickReservationService.class);
        when(access.authorizeTarget(channel, race.getId())).thenReturn(actor);
        when(targets.homeRace(channel, race.getId())).thenReturn(new HomeRaceTarget(race.getId(), "Test race", PickState.MISSING, 0, true, "Test reason"));
        when(races.findById(race.getId())).thenReturn(Optional.of(race));
        when(selections.availability(season.getId(), actor.teamId(), race.getId())).thenReturn(
                new RaceCarSelectionService.Availability(CarPoolMode.FIXED_CARS, CarPoolState.DRAFT, List.of(), Set.of(), Set.of()));
        var service = new PickTeamReadService(access, targets, races, picks, mock(MatchdayPickRevisionRepository.class),
                mock(MatchdayPickDecisionRepository.class), selections, reservations, mock(CarRepository.class), mock(TrackRepository.class),
                new PickDeadlinePolicy(Clock.systemUTC()));
        // when
        var view = service.load(channel, race.getId());
        // then
        assertThat(view.deadlineAt()).isEqualTo(expected == null ? null : Instant.parse(expected));
        if (local == null) assertThat(view.deadline()).isEqualTo("Not configured");
        else if (expected == null) assertThat(view.deadline()).contains("requires management review");
    }
}
