package org.ctc.discord.service;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.util.List;
import java.util.UUID;
import org.ctc.TestHelper;
import org.ctc.admin.dto.RankedTeamData;
import org.ctc.admin.service.PowerRankingsGraphicService;
import org.ctc.discord.model.DiscordGlobalConfig;
import org.ctc.discord.model.DiscordPost;
import org.ctc.discord.model.DiscordPostType;
import org.ctc.discord.repository.DiscordGlobalConfigRepository;
import org.ctc.discord.repository.DiscordPostRepository;
import org.ctc.domain.model.Match;
import org.ctc.domain.model.Matchday;
import org.ctc.domain.model.Season;
import org.ctc.domain.model.SeasonTeam;
import org.ctc.domain.model.Team;
import org.ctc.domain.repository.MatchRepository;
import org.ctc.domain.repository.MatchdayRepository;
import org.ctc.domain.repository.SeasonRepository;
import org.ctc.domain.repository.SeasonTeamRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("dev")
@Tag("integration")
@Transactional
class DiscordPostServicePowerRankingsIT {

	private static final byte[] PNG_BYTES = new byte[] {
			(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
	};
	private static final String THREAD_ID = "thread-pr-99";

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
	DiscordPostService service;

	@Autowired
	TestHelper helper;

	@Autowired
	MatchRepository matchRepository;

	@Autowired
	MatchdayRepository matchdayRepository;

	@Autowired
	SeasonRepository seasonRepository;

	@Autowired
	SeasonTeamRepository seasonTeamRepository;

	@Autowired
	DiscordPostRepository discordPostRepository;

	@Autowired
	DiscordGlobalConfigRepository globalConfigRepository;

	@Autowired
	DiscordGlobalConfigService globalConfigService;

	@MockitoBean
	PowerRankingsGraphicService powerRankingsGraphicService;

	@BeforeEach
	void resetState() throws Exception {
		wm.resetAll();
		discordPostRepository.deleteAll();
		when(powerRankingsGraphicService.generateRankings(anyInt(), anyInt(), anyString(), anyList()))
				.thenReturn(PNG_BYTES);
		wm.stubFor(get(urlPathEqualTo("/api/v10/channels/" + THREAD_ID))
				.willReturn(okJson("{\"id\":\"" + THREAD_ID + "\",\"name\":\"t\",\"type\":11,"
						+ "\"thread_metadata\":{\"archived\":false}}")));
	}

	private Matchday seedMatchday(String suffix, boolean allFinal, int year, int number) {
		Season season = helper.createSeason("PR Season " + suffix);
		season.setYear(year);
		season.setNumber(number);
		season.setDiscordRaceResultsThreadId(THREAD_ID);
		seasonRepository.save(season);
		Matchday md = helper.createMatchdayInRegularPhase(season, "MD-PR-" + suffix, 0);
		Team home = helper.createTeam("PR Home " + suffix, "ph" + suffix);
		Team away = helper.createTeam("PR Away " + suffix, "pa" + suffix);
		seasonTeamRepository.save(new SeasonTeam(season, home));
		seasonTeamRepository.save(new SeasonTeam(season, away));
		Match match = helper.createMatch(md, home, away);
		if (allFinal) {
			match.setHomeScore(3);
			match.setAwayScore(2);
		}
		matchRepository.save(match);
		md.getMatches().add(match);
		matchdayRepository.save(md);
		return md;
	}

	private void setForumWebhook(String webhookUrl) {
		DiscordGlobalConfig cfg = globalConfigService.getOrInitialize();
		cfg.setRaceResultsForumWebhookUrl(webhookUrl);
		globalConfigRepository.save(cfg);
	}

	@Test
	void givenThreadAndWebhook_whenPostPowerRankings_thenMultipartWithThreadIdAndRowPersisted() throws Exception {
		String webhookPath = "/webhooks/990/tok-pr1";
		String webhookUrl = wm.baseUrl() + webhookPath;
		setForumWebhook(webhookUrl);
		Matchday md = seedMatchday("R1", true, 2026, 4);
		UUID teamId1 = UUID.randomUUID();
		UUID teamId2 = UUID.randomUUID();
		when(powerRankingsGraphicService.loadTeamsForSeasonGroup(2026, 4)).thenReturn(List.of(
				new RankedTeamData(teamId1, "Alpha", "ALP", null, "#abc", 1500),
				new RankedTeamData(teamId2, "Beta", "BET", null, "#def", 1400)
		));
		wm.stubFor(post(urlPathEqualTo(webhookPath))
				.withQueryParam("thread_id", equalTo(THREAD_ID))
				.willReturn(okJson("{\"id\":\"msg-pr1\",\"channel_id\":\"990\"}")));

		DiscordPost saved = service.postPowerRankings(md);

		assertThat(saved.getMessageId()).isEqualTo("msg-pr1");
		assertThat(saved.getPostType()).isEqualTo(DiscordPostType.POWER_RANKINGS);
		assertThat(saved.getChannelId()).isEqualTo("990");
		assertThat(saved.getMatchdayId()).isEqualTo(md.getId());

		wm.verify(postRequestedFor(urlPathEqualTo(webhookPath))
				.withQueryParam("thread_id", equalTo(THREAD_ID)));
		String body = wm.findAll(postRequestedFor(urlPathEqualTo(webhookPath))).get(0).getBodyAsString();
		assertThat(body).contains("power-rankings-md-pr-r1.png");
	}

	@Test
	void givenNotAllFinal_whenPostPowerRankings_thenSucceedsBecauseNoFinalityGate() throws Exception {
		String webhookPath = "/webhooks/991/tok-pr2";
		String webhookUrl = wm.baseUrl() + webhookPath;
		setForumWebhook(webhookUrl);
		Matchday md = seedMatchday("R2", false, 2026, 5);
		when(powerRankingsGraphicService.loadTeamsForSeasonGroup(2026, 5)).thenReturn(List.of());
		wm.stubFor(post(urlPathEqualTo(webhookPath))
				.willReturn(okJson("{\"id\":\"msg-pr2\",\"channel_id\":\"991\"}")));

		DiscordPost saved = service.postPowerRankings(md);

		assertThat(saved.getMessageId()).isEqualTo("msg-pr2");
	}

	@Test
	void givenRankedTeams_whenPostPowerRankings_thenTeamIdsPassedToGenerateInRatingOrder() throws Exception {
		String webhookPath = "/webhooks/992/tok-pr3";
		String webhookUrl = wm.baseUrl() + webhookPath;
		setForumWebhook(webhookUrl);
		Matchday md = seedMatchday("R3", true, 2026, 6);
		UUID first = UUID.randomUUID();
		UUID second = UUID.randomUUID();
		UUID third = UUID.randomUUID();
		when(powerRankingsGraphicService.loadTeamsForSeasonGroup(2026, 6)).thenReturn(List.of(
				new RankedTeamData(first, "First", "FST", null, "#111", 2000),
				new RankedTeamData(second, "Second", "SND", null, "#222", 1500),
				new RankedTeamData(third, "Third", "TRD", null, "#333", 1000)
		));
		wm.stubFor(post(urlPathEqualTo(webhookPath))
				.willReturn(okJson("{\"id\":\"msg-pr3\",\"channel_id\":\"992\"}")));

		service.postPowerRankings(md);

		@SuppressWarnings("unchecked")
		ArgumentCaptor<List<UUID>> captor = ArgumentCaptor.forClass(List.class);
		verify(powerRankingsGraphicService).generateRankings(eq(2026), eq(6), anyString(), captor.capture());
		assertThat(captor.getValue()).containsExactly(first, second, third);
	}
}
