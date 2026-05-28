package org.ctc.discord.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.ctc.admin.dto.MatchPreviewPreFlightResult;
import org.ctc.admin.service.LineupGraphicService;
import org.ctc.admin.service.MatchResultsGraphicService;
import org.ctc.admin.service.MatchdayPairingsGraphicService;
import org.ctc.admin.service.MatchdayResultsGraphicService;
import org.ctc.admin.service.MatchdayScheduleGraphicService;
import org.ctc.admin.service.PowerRankingsGraphicService;
import org.ctc.admin.service.ProvisionalScoresGraphicService;
import org.ctc.admin.service.ResultsGraphicService;
import org.ctc.admin.service.SettingsGraphicService;
import org.ctc.admin.service.StandingsGraphicService;
import org.ctc.admin.service.TeamCardService;
import org.ctc.discord.DiscordEmojiCache;
import org.ctc.discord.DiscordHostValidator;
import org.ctc.discord.DiscordRestClient;
import org.ctc.discord.DiscordTimestamps;
import org.ctc.discord.DiscordWebhookClient;
import org.ctc.discord.model.DiscordGlobalConfig;
import org.ctc.discord.repository.DiscordPostRepository;
import org.ctc.domain.model.Match;
import org.ctc.domain.model.Matchday;
import org.ctc.domain.model.Race;
import org.ctc.domain.model.Team;
import org.ctc.domain.repository.RaceLineupRepository;
import org.ctc.domain.repository.SeasonTeamRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("integration")
class DiscordPostServiceByeMatchdayGuardIT {

	private DiscordPostService service;

	@BeforeEach
	void setUp() {
		service = new DiscordPostService(
				mock(DiscordWebhookClient.class),
				mock(DiscordRestClient.class),
				mock(DiscordPostRepository.class),
				mock(DiscordHostValidator.class),
				mock(DiscordGlobalConfigService.class),
				Clock.systemUTC(),
				mock(TeamCardService.class),
				mock(SeasonTeamRepository.class),
				mock(SettingsGraphicService.class),
				mock(LineupGraphicService.class),
				mock(RaceLineupRepository.class),
				mock(MatchResultsGraphicService.class),
				mock(ResultsGraphicService.class),
				mock(ProvisionalScoresGraphicService.class),
				mock(DiscordTimestamps.class),
				mock(DiscordEmojiCache.class),
				mock(MatchdayResultsGraphicService.class),
				mock(PowerRankingsGraphicService.class),
				mock(StandingsGraphicService.class),
				mock(MatchdayPairingsGraphicService.class),
				mock(MatchdayScheduleGraphicService.class),
				"uploads");
	}

	private static Team team(String shortName) {
		Team t = new Team();
		t.setShortName(shortName);
		return t;
	}

	private static Matchday baseMatchday() {
		Matchday md = new Matchday();
		md.setId(UUID.randomUUID());
		md.setLabel("Match Day 1");
		md.setPickDeadline(LocalDateTime.now().plusDays(7));
		md.setScheduledWeekend("22-24 May");
		md.setMatches(new ArrayList<>());
		return md;
	}

	private static Match regularMatchWithRaceTime(Matchday md, String homeShort, String awayShort) {
		Match m = new Match();
		m.setId(UUID.randomUUID());
		m.setMatchday(md);
		m.setHomeTeam(team(homeShort));
		m.setAwayTeam(team(awayShort));
		m.setBye(false);
		Race r = new Race();
		r.setMatch(m);
		r.setMatchday(md);
		r.setDateTime(LocalDateTime.now().plusDays(7));
		m.getRaces().add(r);
		return m;
	}

	private static Match byeMatch(Matchday md, String homeShort) {
		Match m = new Match();
		m.setId(UUID.randomUUID());
		m.setMatchday(md);
		m.setHomeTeam(team(homeShort));
		m.setAwayTeam(null);
		m.setBye(true);
		return m;
	}

	private static DiscordGlobalConfig configWithWebhook() {
		DiscordGlobalConfig c = new DiscordGlobalConfig();
		c.setAnnouncementWebhookUrl("https://discord.com/api/webhooks/1/tok");
		return c;
	}

	@Test
	void givenEmptyMatchday_whenCanPostMatchdayPairings_thenFalse() {
		// given
		Matchday md = baseMatchday();
		DiscordGlobalConfig cfg = configWithWebhook();

		// when
		MatchPreviewPreFlightResult result = service.canPostMatchdayPairings(md, cfg);

		// then
		assertThat(result.canPost()).isFalse();
	}

	@Test
	void givenEmptyMatchday_whenCanPostMatchdaySchedule_thenFalse() {
		// given
		Matchday md = baseMatchday();
		DiscordGlobalConfig cfg = configWithWebhook();

		// when
		MatchPreviewPreFlightResult result = service.canPostMatchdaySchedule(md, cfg);

		// then
		assertThat(result.canPost()).isFalse();
	}

	@Test
	void givenAllByeMatchday_whenCanPostMatchdayPairings_thenFalse() {
		// given
		Matchday md = baseMatchday();
		md.getMatches().add(byeMatch(md, "T-A"));
		md.getMatches().add(byeMatch(md, "T-B"));
		DiscordGlobalConfig cfg = configWithWebhook();

		// when
		MatchPreviewPreFlightResult result = service.canPostMatchdayPairings(md, cfg);

		// then
		assertThat(result.canPost()).isFalse();
	}

	@Test
	void givenAllByeMatchday_whenCanPostMatchdaySchedule_thenFalse() {
		// given
		Matchday md = baseMatchday();
		md.getMatches().add(byeMatch(md, "T-A"));
		md.getMatches().add(byeMatch(md, "T-B"));
		DiscordGlobalConfig cfg = configWithWebhook();

		// when
		MatchPreviewPreFlightResult result = service.canPostMatchdaySchedule(md, cfg);

		// then
		assertThat(result.canPost()).isFalse();
	}

	@Test
	void givenMixedByeAndRegularMatchday_whenCanPostMatchdayPairings_thenTrue() {
		// given
		Matchday md = baseMatchday();
		md.getMatches().add(regularMatchWithRaceTime(md, "T-H1", "T-A1"));
		md.getMatches().add(byeMatch(md, "T-H2"));
		DiscordGlobalConfig cfg = configWithWebhook();

		// when
		MatchPreviewPreFlightResult result = service.canPostMatchdayPairings(md, cfg);

		// then
		assertThat(result.canPost()).as(result.disabledReason()).isTrue();
	}

	@Test
	void givenMixedByeAndRegularMatchday_whenCanPostMatchdaySchedule_thenTrue() {
		// given
		Matchday md = baseMatchday();
		md.getMatches().add(regularMatchWithRaceTime(md, "T-H1", "T-A1"));
		md.getMatches().add(byeMatch(md, "T-H2"));
		DiscordGlobalConfig cfg = configWithWebhook();

		// when
		MatchPreviewPreFlightResult result = service.canPostMatchdaySchedule(md, cfg);

		// then
		assertThat(result.canPost()).as(result.disabledReason()).isTrue();
	}
}
