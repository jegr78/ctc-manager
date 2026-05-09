package org.ctc.domain.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ctc.dataimport.GoogleCalendarService;
import org.ctc.domain.repository.RaceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.UUID;

/**
 * Service for managing Google Calendar events for races.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RaceCalendarService {

	private final RaceRepository raceRepository;
	private final GoogleCalendarService googleCalendarService;

	public boolean isCalendarAvailable() {
		return googleCalendarService.isAvailable();
	}

	@Transactional
	public void createOrUpdateCalendarEvent(UUID raceId) throws IOException {
		var race = raceRepository.findById(raceId).orElseThrow();

		if (!googleCalendarService.isAvailable()) {
			throw new IllegalStateException("Google Calendar integration not available");
		}
		if (race.getDateTime() == null) {
			throw new IllegalStateException("Race has no date/time set");
		}
		if (race.getHomeTeam() == null || race.getAwayTeam() == null) {
			throw new IllegalStateException("Race has no teams assigned");
		}

		Integer durationMinutes = resolveEventDuration(race);
		if (durationMinutes == null) {
			throw new IllegalStateException("Event duration not configured. Set it in the season or playoff form.");
		}

		String title = race.getMatchday().getLabel() + " - "
				+ race.getHomeTeam().getShortName() + " vs. "
				+ race.getAwayTeam().getShortName();

		if (race.hasCalendarEvent()) {
			googleCalendarService.updateEvent(race.getCalendarEventId(), title, race.getDateTime(), durationMinutes);
			log.info("Updated calendar event for race {}: {}", raceId, title);
		} else {
			String eventId = googleCalendarService.createEvent(title, race.getDateTime(), durationMinutes);
			race.setCalendarEventId(eventId);
			raceRepository.save(race);
			log.info("Created calendar event for race {}: {} (eventId: {})", raceId, title, eventId);
		}
	}

	private Integer resolveEventDuration(org.ctc.domain.model.Race race) {
		if (race.getPlayoffMatchup() != null) {
			var playoffDuration = race.getPlayoffMatchup().getRound().getPlayoff().getEventDurationMinutes();
			if (playoffDuration != null) {
				return playoffDuration;
			}
		}
		// eventDurationMinutes lives on the SeasonPhase. Guarding the matchday + phase chain
		// surfaces missing config as the IllegalStateException at the caller, not an opaque NPE.
		var matchday = race.getMatchday();
		if (matchday == null || matchday.getPhase() == null) {
			return null;
		}
		return matchday.getPhase().getEventDurationMinutes();
	}
}
