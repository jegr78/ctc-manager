package org.ctc.discord.service;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.time.LocalDateTime;
import org.ctc.TestHelper;
import org.ctc.discord.event.MatchScheduleFieldsChangedEvent;
import org.ctc.domain.model.Match;
import org.ctc.domain.model.Matchday;
import org.ctc.domain.model.Race;
import org.ctc.domain.model.Season;
import org.ctc.domain.model.Team;
import org.ctc.domain.repository.MatchRepository;
import org.ctc.domain.repository.RaceLineupRepository;
import org.ctc.domain.repository.RaceRepository;
import org.ctc.domain.service.RaceService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

@SpringBootTest
@ActiveProfiles("dev")
@Tag("integration")
@RecordApplicationEvents
class DiscordAutoPostListenerScheduleEditIT {

	@RegisterExtension
	static WireMockExtension wm = WireMockExtension.newInstance()
			.options(options().dynamicPort())
			.build();

	@DynamicPropertySource
	static void overrideDiscordConfig(DynamicPropertyRegistry registry) {
		registry.add("app.discord.base-url", () -> wm.baseUrl() + "/api/v10");
		registry.add("app.discord.bot-token", () -> "test-bot-token");
		registry.add("app.discord.allowed-hosts", () -> "discord.com,localhost,127.0.0.1");
		registry.add("app.discord.rate-limit.jitter-ms", () -> "0");
		registry.add("app.discord.rate-limit.fivexx-backoff-ms", () -> "10,10,10");
	}

	@Autowired
	RaceService raceService;

	@Autowired
	RaceRepository raceRepository;

	@Autowired
	MatchRepository matchRepository;

	@Autowired
	RaceLineupRepository raceLineupRepository;

	@Autowired
	TestHelper helper;

	@Autowired
	ApplicationEvents applicationEvents;

	@BeforeEach
	void resetWireMock() {
		wm.resetAll();
		wm.stubFor(get(urlPathEqualTo("/api/v10/users/@me"))
				.willReturn(okJson("{\"id\":\"bot-1\",\"username\":\"CTC-Bot\",\"discriminator\":\"0001\"}")));
	}

	@AfterEach
	void cleanupRaces() {
		raceLineupRepository.deleteAll();
		raceRepository.deleteAll();
		matchRepository.deleteAll();
	}

	private record Fixture(Race race, Match match, LocalDateTime initialDateTime) {}

	private Fixture seedRaceWithDateTime(String prefix, LocalDateTime initialDateTime) {
		Season season = helper.createSeason(prefix + "-Schedule-Season");
		Matchday md = helper.createMatchdayInRegularPhase(season, prefix + "-MD", 0);
		Team home = helper.createTeam(prefix + " Home", prefix + "h");
		Team away = helper.createTeam(prefix + " Away", prefix + "a");
		Match match = helper.createMatch(md, home, away);
		Race race = helper.createRace(md, match);
		race.setDateTime(initialDateTime);
		raceRepository.save(race);
		return new Fixture(race, match, initialDateTime);
	}

	@Test
	void givenDateTimeChanges_whenSaveRace_thenScheduleEventPublishedAfterCommit() {
		// given
		LocalDateTime t0 = LocalDateTime.of(2026, 6, 1, 20, 0);
		LocalDateTime t1 = LocalDateTime.of(2026, 6, 1, 21, 30);
		Fixture fx = seedRaceWithDateTime("DT-Change", t0);

		// when
		raceService.saveRace(fx.race.getId(),
				fx.race.getMatchday().getId(),
				fx.match.getHomeTeam().getId(),
				fx.match.getAwayTeam().getId(),
				null, null, t1,
				null, null, null, null, null, null, null, null, null, null, null);

		// then
		long eventCount = applicationEvents.stream(MatchScheduleFieldsChangedEvent.class)
				.filter(e -> e.matchId().equals(fx.match.getId()))
				.count();
		assertThat(eventCount).isEqualTo(1L);
	}

	@Test
	void givenDateTimeUnchanged_whenSaveRace_thenNoScheduleEventPublished() {
		// given
		LocalDateTime t0 = LocalDateTime.of(2026, 7, 1, 19, 0);
		Fixture fx = seedRaceWithDateTime("DT-Same", t0);

		// when
		raceService.saveRace(fx.race.getId(),
				fx.race.getMatchday().getId(),
				fx.match.getHomeTeam().getId(),
				fx.match.getAwayTeam().getId(),
				null, null, t0,
				null, null, null, null, null, null, null, null, null, null, null);

		// then
		long eventCount = applicationEvents.stream(MatchScheduleFieldsChangedEvent.class)
				.filter(e -> e.matchId().equals(fx.match.getId()))
				.count();
		assertThat(eventCount).isZero();
	}
}
