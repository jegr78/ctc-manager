package org.ctc.domain.service;

import org.ctc.dataimport.GoogleCalendarService;
import org.ctc.domain.model.*;
import org.ctc.domain.repository.RaceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RaceCalendarServiceTest {

	@Mock
	private RaceRepository raceRepository;
	@Mock
	private GoogleCalendarService googleCalendarService;

	@InjectMocks
	private RaceCalendarService service;

	// --- createOrUpdateCalendarEvent ---

	@Test
	void givenRaceWithDateTimeAndTeams_whenCreateOrUpdateCalendarEvent_thenDelegatesToGoogleCalendarService() throws Exception {
		// given
		var homeTeam = createTeam("DTR", "Delta Racing");
		var awayTeam = createTeam("MRL", "Maranello");
		// eventDurationMinutes is owned by the REGULAR phase.
		var matchday = matchdayWithEventDuration(90);
		matchday.setLabel("MD 3");
		var match = new Match(matchday, homeTeam, awayTeam);
		var race = new Race();
		race.setId(UUID.randomUUID());
		race.setMatchday(matchday);
		race.setMatch(match);
		race.setDateTime(LocalDateTime.of(2026, 5, 1, 19, 30));

		when(raceRepository.findById(race.getId())).thenReturn(Optional.of(race));
		when(googleCalendarService.isAvailable()).thenReturn(true);
		when(googleCalendarService.createEvent(eq("MD 3 - DTR vs. MRL"),
				eq(LocalDateTime.of(2026, 5, 1, 19, 30)), eq(90)))
				.thenReturn("new-event-id");
		when(raceRepository.save(any(Race.class))).thenAnswer(inv -> inv.getArgument(0));

		// when
		service.createOrUpdateCalendarEvent(race.getId());

		// then
		verify(googleCalendarService).createEvent("MD 3 - DTR vs. MRL",
				LocalDateTime.of(2026, 5, 1, 19, 30), 90);
		verify(raceRepository).save(argThat(r -> "new-event-id".equals(r.getCalendarEventId())));
	}

	@Test
	void givenRaceWithExistingCalendarEventId_whenCreateOrUpdateCalendarEvent_thenUpdatesExistingEvent() throws Exception {
		// given
		var homeTeam = createTeam("HOM", "Home");
		var awayTeam = createTeam("AWY", "Away");
		// eventDurationMinutes is owned by the REGULAR phase.
		var matchday = matchdayWithEventDuration(60);
		matchday.setLabel("MD 1");
		var match = new Match(matchday, homeTeam, awayTeam);
		var race = new Race();
		race.setId(UUID.randomUUID());
		race.setMatchday(matchday);
		race.setMatch(match);
		race.setDateTime(LocalDateTime.of(2026, 6, 10, 20, 0));
		race.setCalendarEventId("existing-event-999");

		when(raceRepository.findById(race.getId())).thenReturn(Optional.of(race));
		when(googleCalendarService.isAvailable()).thenReturn(true);

		// when
		service.createOrUpdateCalendarEvent(race.getId());

		// then
		verify(googleCalendarService).updateEvent("existing-event-999", "MD 1 - HOM vs. AWY",
				LocalDateTime.of(2026, 6, 10, 20, 0), 60);
		verify(googleCalendarService, never()).createEvent(any(), any(), anyInt());
	}

	@Test
	void givenCalendarNotAvailable_whenCreateOrUpdateCalendarEvent_thenThrowsIllegalStateException() {
		// given
		var race = new Race();
		race.setId(UUID.randomUUID());
		race.setMatchday(createMatchday());

		when(raceRepository.findById(race.getId())).thenReturn(Optional.of(race));
		when(googleCalendarService.isAvailable()).thenReturn(false);

		// when / then
		assertThatThrownBy(() -> service.createOrUpdateCalendarEvent(race.getId()))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("Calendar");
	}

	@Test
	void givenRaceWithoutDateTime_whenCreateOrUpdateCalendarEvent_thenThrowsIllegalStateException() {
		// given
		var race = new Race();
		race.setId(UUID.randomUUID());
		race.setMatchday(createMatchday());

		when(raceRepository.findById(race.getId())).thenReturn(Optional.of(race));
		when(googleCalendarService.isAvailable()).thenReturn(true);

		// when / then
		assertThatThrownBy(() -> service.createOrUpdateCalendarEvent(race.getId()))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("date/time");
	}

	@Test
	void givenCalendarServiceThrowsIOException_whenCreateOrUpdateCalendarEvent_thenPropagatesException() throws Exception {
		// given
		var homeTeam = createTeam("HOM", "Home");
		var awayTeam = createTeam("AWY", "Away");
		// eventDurationMinutes is owned by the REGULAR phase.
		var matchday = matchdayWithEventDuration(60);
		matchday.setLabel("MD 1");
		var match = new Match(matchday, homeTeam, awayTeam);
		var race = new Race();
		race.setId(UUID.randomUUID());
		race.setMatchday(matchday);
		race.setMatch(match);
		race.setDateTime(LocalDateTime.of(2026, 6, 10, 20, 0));

		when(raceRepository.findById(race.getId())).thenReturn(Optional.of(race));
		when(googleCalendarService.isAvailable()).thenReturn(true);
		when(googleCalendarService.createEvent(any(), any(), anyInt()))
				.thenThrow(new IOException("Calendar API error"));

		// when / then
		assertThatThrownBy(() -> service.createOrUpdateCalendarEvent(race.getId()))
				.isInstanceOf(IOException.class)
				.hasMessageContaining("Calendar API error");
	}

	// --- Helper ---

	private Team createTeam(String shortName, String name) {
		var team = new Team(name, shortName);
		team.setId(UUID.randomUUID());
		return team;
	}

	private Matchday createMatchday() {
		var season = new Season();
		season.setId(UUID.randomUUID());
		var matchday = new Matchday();
		matchday.setId(UUID.randomUUID());
		matchday.setPhase(PhaseTestFixtures.regularPhase(season, null, null));
		return matchday;
	}

	/**
	 * builds a Matchday wired to a REGULAR phase carrying
	 * the given {@code eventDurationMinutes}. Replaces the legacy pattern of
	 * {@code season.setEventDurationMinutes(...)} + {@code matchday.setSeason(...)}.
	 */
	private Matchday matchdayWithEventDuration(int eventDurationMinutes) {
		var season = new Season();
		season.setId(UUID.randomUUID());
		var phase = PhaseTestFixtures.regularPhase(season, null, null);
		phase.setEventDurationMinutes(eventDurationMinutes);
		var matchday = new Matchday();
		matchday.setId(UUID.randomUUID());
		matchday.setPhase(phase);
		return matchday;
	}
}
