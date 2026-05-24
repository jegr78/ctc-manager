package org.ctc.discord.service;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.patch;
import static com.github.tomakehurst.wiremock.client.WireMock.patchRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.util.List;
import org.ctc.TestHelper;
import org.ctc.admin.service.StandingsGraphicService;
import org.ctc.discord.model.DiscordGlobalConfig;
import org.ctc.discord.model.DiscordPost;
import org.ctc.discord.model.DiscordPostType;
import org.ctc.discord.repository.DiscordGlobalConfigRepository;
import org.ctc.discord.repository.DiscordPostRepository;
import org.ctc.domain.model.PhaseLayout;
import org.ctc.domain.model.PhaseType;
import org.ctc.domain.model.Season;
import org.ctc.domain.model.SeasonPhase;
import org.ctc.domain.model.SeasonPhaseGroup;
import org.ctc.domain.repository.SeasonPhaseRepository;
import org.ctc.domain.repository.SeasonRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
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
class DiscordPostServiceStandingsIT {

	private static final byte[] PNG_BYTES = new byte[] {
			(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
	};
	private static final String THREAD_ID = "thread-st-99";

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
	SeasonRepository seasonRepository;

	@Autowired
	SeasonPhaseRepository seasonPhaseRepository;

	@Autowired
	DiscordPostRepository discordPostRepository;

	@Autowired
	DiscordGlobalConfigRepository globalConfigRepository;

	@Autowired
	DiscordGlobalConfigService globalConfigService;

	@MockitoBean
	StandingsGraphicService standingsGraphicService;

	@BeforeEach
	void resetState() throws Exception {
		wm.resetAll();
		discordPostRepository.deleteAll();
		when(standingsGraphicService.generateStandingsBytes(any(Season.class), any(SeasonPhase.class)))
				.thenReturn(List.of(PNG_BYTES));
		wm.stubFor(get(urlPathEqualTo("/api/v10/channels/" + THREAD_ID))
				.willReturn(okJson("{\"id\":\"" + THREAD_ID + "\",\"name\":\"t\",\"type\":11,"
						+ "\"thread_metadata\":{\"archived\":false}}")));
	}

	private Season seedSeasonWithRegularPhase(String suffix) {
		Season season = helper.createSeason("ST Season " + suffix);
		season.setDiscordStandingsThreadId(THREAD_ID);
		return seasonRepository.save(season);
	}

	private SeasonPhase addPlayoffPhase(Season season, String suffix) {
		SeasonPhase phase = new SeasonPhase(season, PhaseType.PLAYOFF, PhaseLayout.BRACKET, 1);
		SeasonPhase saved = seasonPhaseRepository.save(phase);
		season.getPhases().add(saved);
		return saved;
	}

	private SeasonPhase addGroupsPhase(Season season, String suffix) {
		SeasonPhase phase = new SeasonPhase(season, PhaseType.PLACEMENT, PhaseLayout.GROUPS, 2);
		SeasonPhase saved = seasonPhaseRepository.save(phase);
		SeasonPhaseGroup gA = new SeasonPhaseGroup();
		gA.setPhase(saved);
		gA.setName("Group A");
		gA.setSortIndex(1);
		SeasonPhaseGroup gB = new SeasonPhaseGroup();
		gB.setPhase(saved);
		gB.setName("Group B");
		gB.setSortIndex(2);
		saved.getGroups().add(gA);
		saved.getGroups().add(gB);
		return seasonPhaseRepository.save(saved);
	}

	private void setStandingsWebhook(String webhookUrl) {
		DiscordGlobalConfig cfg = globalConfigService.getOrInitialize();
		cfg.setStandingsForumWebhookUrl(webhookUrl);
		globalConfigRepository.save(cfg);
	}

	@Test
	void givenRegularLeaguePhase_whenPostStandings_thenSinglePngWithRegularFilename() throws Exception {
		String webhookPath = "/webhooks/600/tok-st1";
		String webhookUrl = wm.baseUrl() + webhookPath;
		setStandingsWebhook(webhookUrl);
		Season season = seedSeasonWithRegularPhase("S1");
		SeasonPhase phase = season.getPhases().get(0);
		wm.stubFor(post(urlPathEqualTo(webhookPath))
				.withQueryParam("thread_id", equalTo(THREAD_ID))
				.willReturn(okJson("{\"id\":\"msg-st1\",\"channel_id\":\"600\"}")));

		DiscordPost saved = service.postStandings(season, phase);

		assertThat(saved.getMessageId()).isEqualTo("msg-st1");
		assertThat(saved.getPostType()).isEqualTo(DiscordPostType.STANDINGS);
		assertThat(saved.getSeasonId()).isEqualTo(season.getId());
		assertThat(saved.getPhaseId()).isEqualTo(phase.getId());

		String body = wm.findAll(postRequestedFor(urlPathEqualTo(webhookPath))).get(0).getBodyAsString();
		assertThat(body).contains("standings-regular.png");
	}

	@Test
	void givenGroupsPhaseWithTwoGroups_whenPostStandings_thenTwoPngsInSortIndexOrder() throws Exception {
		String webhookPath = "/webhooks/601/tok-st2";
		String webhookUrl = wm.baseUrl() + webhookPath;
		setStandingsWebhook(webhookUrl);
		Season season = seedSeasonWithRegularPhase("S2");
		SeasonPhase groupsPhase = addGroupsPhase(season, "S2");
		when(standingsGraphicService.generateStandingsBytes(any(Season.class), any(SeasonPhase.class)))
				.thenReturn(List.of(PNG_BYTES, PNG_BYTES));
		wm.stubFor(post(urlPathEqualTo(webhookPath))
				.willReturn(okJson("{\"id\":\"msg-st2\",\"channel_id\":\"601\"}")));

		service.postStandings(season, groupsPhase);

		String body = wm.findAll(postRequestedFor(urlPathEqualTo(webhookPath))).get(0).getBodyAsString();
		assertThat(body).contains("standings-placement-group-a.png");
		assertThat(body).contains("standings-placement-group-b.png");
		int posA = body.indexOf("group-a.png");
		int posB = body.indexOf("group-b.png");
		assertThat(posA).isLessThan(posB);
	}

	@Test
	void givenPlayoffPhase_whenPostStandings_thenSinglePngWithPlayoffFilename() throws Exception {
		String webhookPath = "/webhooks/602/tok-st3";
		String webhookUrl = wm.baseUrl() + webhookPath;
		setStandingsWebhook(webhookUrl);
		Season season = seedSeasonWithRegularPhase("S3");
		SeasonPhase playoffPhase = addPlayoffPhase(season, "S3");
		wm.stubFor(post(urlPathEqualTo(webhookPath))
				.willReturn(okJson("{\"id\":\"msg-st3\",\"channel_id\":\"602\"}")));

		service.postStandings(season, playoffPhase);

		String body = wm.findAll(postRequestedFor(urlPathEqualTo(webhookPath))).get(0).getBodyAsString();
		assertThat(body).contains("standings-playoff.png");
	}

	@Test
	void givenExistingRow_whenPostStandingsAgain_thenPatchAndRowKeepsIdentity() throws Exception {
		String webhookPath = "/webhooks/603/tok-st4";
		String webhookUrl = wm.baseUrl() + webhookPath;
		setStandingsWebhook(webhookUrl);
		Season season = seedSeasonWithRegularPhase("S4");
		SeasonPhase phase = season.getPhases().get(0);
		wm.stubFor(post(urlPathEqualTo(webhookPath))
				.willReturn(okJson("{\"id\":\"msg-st4\",\"channel_id\":\"603\"}")));
		DiscordPost first = service.postStandings(season, phase);

		wm.stubFor(patch(urlPathEqualTo(webhookPath + "/messages/msg-st4"))
				.willReturn(okJson("{\"id\":\"msg-st4\",\"channel_id\":\"603\"}")));
		DiscordPost second = service.postStandings(season, phase);

		assertThat(second.getId()).isEqualTo(first.getId());
		assertThat(second.getPhaseId()).isEqualTo(phase.getId());
		wm.verify(patchRequestedFor(urlPathEqualTo(webhookPath + "/messages/msg-st4")));
	}
}
