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
import org.ctc.domain.model.Team;
import org.ctc.domain.repository.RaceLineupRepository;
import org.ctc.domain.repository.SeasonTeamRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DiscordPostServiceMatchdayPairingsPreFlightTest {

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
				"uploads");
	}

	private static Matchday validMatchday() {
		Matchday md = new Matchday();
		md.setId(UUID.randomUUID());
		md.setLabel("Match Day 1");
		md.setPickDeadline(LocalDateTime.now().plusDays(7));
		md.setScheduledWeekend("22-24 May");
		List<Match> matches = new ArrayList<>();
		for (int i = 0; i < 3; i++) {
			Match m = new Match();
			m.setId(UUID.randomUUID());
			m.setHomeTeam(team("T-HOME-" + i));
			m.setAwayTeam(team("T-AWAY-" + i));
			matches.add(m);
		}
		md.setMatches(matches);
		return md;
	}

	private static Team team(String shortName) {
		Team t = new Team();
		t.setId(UUID.randomUUID());
		t.setShortName(shortName);
		t.setName("Test " + shortName);
		return t;
	}

	private static DiscordGlobalConfig validConfig() {
		DiscordGlobalConfig c = new DiscordGlobalConfig();
		c.setAnnouncementWebhookUrl("https://discord.com/api/webhooks/900000000000000099/test-token-99");
		return c;
	}

	@Test
	void givenAllPreFlightOk_whenCanPostMatchdayPairings_thenAccepts() {
		MatchPreviewPreFlightResult result = service.canPostMatchdayPairings(validMatchday(), validConfig());

		assertThat(result.canPost()).isTrue();
		assertThat(result.disabledReason()).isNull();
	}

	@Test
	void givenAllPreFlightOk_whenCanPostMatchdayPairings_thenAcceptsEvenWithBlankTemplate() {
		DiscordGlobalConfig config = validConfig();
		config.setMatchdayPairingsTemplate(null);

		MatchPreviewPreFlightResult result = service.canPostMatchdayPairings(validMatchday(), config);

		assertThat(result.canPost()).isTrue();
	}

	@Test
	void givenMissingPickDeadline_whenCanPostMatchdayPairings_thenRejectsWithReason() {
		Matchday md = validMatchday();
		md.setPickDeadline(null);

		MatchPreviewPreFlightResult result = service.canPostMatchdayPairings(md, validConfig());

		assertThat(result.canPost()).isFalse();
		assertThat(result.disabledReason()).isEqualTo("Set pick deadline first");
	}

	@Test
	void givenBlankScheduledWeekend_whenCanPostMatchdayPairings_thenRejectsWithReason() {
		Matchday mdNull = validMatchday();
		mdNull.setScheduledWeekend(null);
		Matchday mdBlank = validMatchday();
		mdBlank.setScheduledWeekend("   ");

		assertThat(service.canPostMatchdayPairings(mdNull, validConfig()).disabledReason())
				.isEqualTo("Set scheduled weekend first");
		assertThat(service.canPostMatchdayPairings(mdBlank, validConfig()).disabledReason())
				.isEqualTo("Set scheduled weekend first");
	}

	@Test
	void givenMatchWithoutHomeTeam_whenCanPostMatchdayPairings_thenRejectsWithReason() {
		Matchday md = validMatchday();
		md.getMatches().get(1).setHomeTeam(null);

		MatchPreviewPreFlightResult result = service.canPostMatchdayPairings(md, validConfig());

		assertThat(result.canPost()).isFalse();
		assertThat(result.disabledReason()).isEqualTo("Assign teams to all matches first");
	}

	@Test
	void givenMatchWithoutAwayTeam_whenCanPostMatchdayPairings_thenRejectsWithReason() {
		Matchday md = validMatchday();
		md.getMatches().get(2).setAwayTeam(null);

		MatchPreviewPreFlightResult result = service.canPostMatchdayPairings(md, validConfig());

		assertThat(result.canPost()).isFalse();
		assertThat(result.disabledReason()).isEqualTo("Assign teams to all matches first");
	}

	@Test
	void givenBlankAnnouncementWebhook_whenCanPostMatchdayPairings_thenRejectsWithReason() {
		DiscordGlobalConfig configNull = validConfig();
		configNull.setAnnouncementWebhookUrl(null);
		DiscordGlobalConfig configBlank = validConfig();
		configBlank.setAnnouncementWebhookUrl("   ");

		assertThat(service.canPostMatchdayPairings(validMatchday(), configNull).disabledReason())
				.isEqualTo("Configure announcement-webhook in Discord settings");
		assertThat(service.canPostMatchdayPairings(validMatchday(), configBlank).disabledReason())
				.isEqualTo("Configure announcement-webhook in Discord settings");
	}
}
