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
import org.ctc.domain.model.Race;
import org.ctc.domain.repository.RaceLineupRepository;
import org.ctc.domain.repository.SeasonTeamRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DiscordPostServiceMatchdaySchedulePreFlightTest {

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
				mock(org.ctc.admin.service.MatchdayScheduleGraphicService.class),
				"uploads");
	}

	private static Matchday validMatchday() {
		Matchday md = new Matchday();
		md.setId(UUID.randomUUID());
		md.setLabel("Match Day 1");
		List<Match> matches = new ArrayList<>();
		for (int i = 0; i < 3; i++) {
			Match m = new Match();
			m.setId(UUID.randomUUID());
			Race r = new Race();
			r.setId(UUID.randomUUID());
			r.setDateTime(LocalDateTime.of(2026, 5, 30, 19, 0).plusMinutes(i * 30L));
			m.getRaces().add(r);
			matches.add(m);
		}
		md.setMatches(matches);
		return md;
	}

	private static DiscordGlobalConfig validConfig() {
		DiscordGlobalConfig c = new DiscordGlobalConfig();
		c.setAnnouncementWebhookUrl("https://discord.com/api/webhooks/900000000000000099/test-token-99");
		return c;
	}

	@Test
	void givenAllPreFlightOk_whenCanPostMatchdaySchedule_thenAccepts() {
		MatchPreviewPreFlightResult result = service.canPostMatchdaySchedule(validMatchday(), validConfig());

		assertThat(result.canPost()).isTrue();
		assertThat(result.disabledReason()).isNull();
	}

	@Test
	void givenByeMatchWithoutRaces_whenCanPostMatchdaySchedule_thenStillAcceptsBecauseByeIsSkipped() {
		Matchday md = validMatchday();
		Match bye = new Match();
		bye.setId(UUID.randomUUID());
		bye.setBye(true);
		md.getMatches().add(bye);

		MatchPreviewPreFlightResult result = service.canPostMatchdaySchedule(md, validConfig());

		assertThat(result.canPost()).isTrue();
	}

	@Test
	void givenMatchWithoutRaceDateTime_whenCanPostMatchdaySchedule_thenRejectsWithReason() {
		Matchday md = validMatchday();
		md.getMatches().get(1).getRaces().get(0).setDateTime(null);

		MatchPreviewPreFlightResult result = service.canPostMatchdaySchedule(md, validConfig());

		assertThat(result.canPost()).isFalse();
		assertThat(result.disabledReason()).isEqualTo("Set Race date+time for all matches first");
	}

	@Test
	void givenBlankAnnouncementWebhook_whenCanPostMatchdaySchedule_thenRejectsWithReason() {
		DiscordGlobalConfig configNull = validConfig();
		configNull.setAnnouncementWebhookUrl(null);
		DiscordGlobalConfig configBlank = validConfig();
		configBlank.setAnnouncementWebhookUrl("   ");

		assertThat(service.canPostMatchdaySchedule(validMatchday(), configNull).disabledReason())
				.isEqualTo("Configure announcement-webhook in Discord settings");
		assertThat(service.canPostMatchdaySchedule(validMatchday(), configBlank).disabledReason())
				.isEqualTo("Configure announcement-webhook in Discord settings");
	}
}
