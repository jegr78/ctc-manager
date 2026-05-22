package org.ctc.discord.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import org.ctc.discord.DiscordHostValidator;
import org.ctc.discord.DiscordWebhookClient;
import org.ctc.discord.repository.DiscordPostRepository;
import org.ctc.domain.model.Match;
import org.ctc.domain.model.Race;
import org.ctc.domain.model.RaceLineup;
import org.ctc.domain.model.RaceSettings;
import org.ctc.domain.repository.RaceLineupRepository;
import org.ctc.domain.repository.SeasonTeamRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DiscordPostServicePreFlightTest {

	private RaceLineupRepository raceLineupRepository;
	private DiscordPostService service;

	@BeforeEach
	void setUp() {
		raceLineupRepository = mock(RaceLineupRepository.class);
		service = new DiscordPostService(
				mock(DiscordWebhookClient.class),
				mock(DiscordPostRepository.class),
				mock(DiscordHostValidator.class),
				java.time.Clock.systemUTC(),
				mock(org.ctc.admin.service.TeamCardService.class),
				mock(SeasonTeamRepository.class),
				mock(org.ctc.admin.service.SettingsGraphicService.class),
				mock(org.ctc.admin.service.LineupGraphicService.class),
				raceLineupRepository,
				"uploads");
	}

	private static Race raceWithSettings(boolean hasSettings) {
		Race race = new Race();
		race.setId(UUID.randomUUID());
		if (hasSettings) {
			race.setSettings(new RaceSettings(race));
		}
		return race;
	}

	private static Match matchWithRaces(List<Race> races) {
		Match m = new Match();
		m.setId(UUID.randomUUID());
		m.getRaces().addAll(races);
		return m;
	}

	@Test
	void givenAllRacesHaveSettings_whenMatchHasCompleteSettings_thenTrue() {
		Match match = matchWithRaces(List.of(raceWithSettings(true), raceWithSettings(true)));
		assertThat(service.matchHasCompleteSettings(match)).isTrue();
	}

	@Test
	void givenOneRaceMissingSettings_whenMatchHasCompleteSettings_thenFalse() {
		Match match = matchWithRaces(List.of(raceWithSettings(true), raceWithSettings(false)));
		assertThat(service.matchHasCompleteSettings(match)).isFalse();
	}

	@Test
	void givenEmptyRaces_whenMatchHasCompleteSettings_thenFalse() {
		Match match = matchWithRaces(List.of());
		assertThat(service.matchHasCompleteSettings(match)).isFalse();
	}

	@Test
	void givenAllRacesHaveLineups_whenMatchHasCompleteLineups_thenTrue() {
		Race r1 = raceWithSettings(false);
		Race r2 = raceWithSettings(false);
		when(raceLineupRepository.findByRaceId(r1.getId()))
				.thenReturn(List.of(mock(RaceLineup.class)));
		when(raceLineupRepository.findByRaceId(r2.getId()))
				.thenReturn(List.of(mock(RaceLineup.class), mock(RaceLineup.class)));

		Match match = matchWithRaces(List.of(r1, r2));
		assertThat(service.matchHasCompleteLineups(match)).isTrue();
	}

	@Test
	void givenOneRaceHasEmptyLineup_whenMatchHasCompleteLineups_thenFalse() {
		Race r1 = raceWithSettings(false);
		Race r2 = raceWithSettings(false);
		when(raceLineupRepository.findByRaceId(r1.getId()))
				.thenReturn(List.of(mock(RaceLineup.class)));
		when(raceLineupRepository.findByRaceId(r2.getId())).thenReturn(List.of());

		Match match = matchWithRaces(List.of(r1, r2));
		assertThat(service.matchHasCompleteLineups(match)).isFalse();
	}

	@Test
	void givenEmptyRaces_whenMatchHasCompleteLineups_thenFalse() {
		Match match = matchWithRaces(List.of());
		assertThat(service.matchHasCompleteLineups(match)).isFalse();
	}
}
