package org.ctc.discord.service;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.ctc.TestHelper;
import org.ctc.admin.service.MatchdayResultsGraphicService;
import org.ctc.discord.exception.DiscordApiException;
import org.ctc.discord.exception.DiscordApiException.Category;
import org.ctc.discord.model.DiscordGlobalConfig;
import org.ctc.discord.model.DiscordPost;
import org.ctc.discord.model.DiscordPostType;
import org.ctc.discord.repository.DiscordGlobalConfigRepository;
import org.ctc.discord.repository.DiscordPostRepository;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.model.Match;
import org.ctc.domain.model.Matchday;
import org.ctc.domain.model.Season;
import org.ctc.domain.model.Team;
import org.ctc.domain.repository.MatchRepository;
import org.ctc.domain.repository.MatchdayRepository;
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
class DiscordPostServiceMatchdayResultsIT {

	private static final byte[] PNG_BYTES = new byte[] {
			(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
	};
	private static final String THREAD_ID = "thread-md-99";

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
	DiscordPostRepository discordPostRepository;

	@Autowired
	DiscordGlobalConfigRepository globalConfigRepository;

	@Autowired
	DiscordGlobalConfigService globalConfigService;

	@MockitoBean
	MatchdayResultsGraphicService matchdayResultsGraphicService;

	@BeforeEach
	void resetState() throws Exception {
		wm.resetAll();
		discordPostRepository.deleteAll();
		when(matchdayResultsGraphicService.generateResults(any(Matchday.class))).thenReturn(PNG_BYTES);
		wm.stubFor(get(urlPathEqualTo("/api/v10/channels/" + THREAD_ID))
				.willReturn(okJson("{\"id\":\"" + THREAD_ID + "\",\"name\":\"t\",\"type\":11,"
						+ "\"thread_metadata\":{\"archived\":false}}")));
	}

	private Matchday seedMatchday(String suffix, boolean allFinal) {
		Season season = helper.createSeason("MD Season " + suffix);
		season.setDiscordRaceResultsThreadId(THREAD_ID);
		Matchday md = helper.createMatchdayInRegularPhase(season, "MD-MD-" + suffix, 0);
		Team home = helper.createTeam("MD Home " + suffix, "mh" + suffix);
		Team away = helper.createTeam("MD Away " + suffix, "ma" + suffix);
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
	void givenAllFinalAndThreadAndWebhook_whenPostMatchdayResults_thenMultipartWithThreadIdAndRowPersisted() throws Exception {
		String webhookPath = "/webhooks/980/tok-md1";
		String webhookUrl = wm.baseUrl() + webhookPath;
		setForumWebhook(webhookUrl);
		Matchday md = seedMatchday("M1", true);
		wm.stubFor(post(urlPathEqualTo(webhookPath))
				.withQueryParam("thread_id", equalTo(THREAD_ID))
				.willReturn(okJson("{\"id\":\"msg-md1\",\"channel_id\":\"980\"}")));

		DiscordPost saved = service.postMatchdayResults(md);

		assertThat(saved.getMessageId()).isEqualTo("msg-md1");
		assertThat(saved.getPostType()).isEqualTo(DiscordPostType.MATCHDAY_OVERVIEW);
		assertThat(saved.getChannelId()).isEqualTo("980");
		assertThat(saved.getMatchdayId()).isEqualTo(md.getId());
		assertThat(saved.getAttachmentsReplacedAt()).isNotNull();

		wm.verify(postRequestedFor(urlPathEqualTo(webhookPath))
				.withQueryParam("thread_id", equalTo(THREAD_ID)));
		String body = wm.findAll(postRequestedFor(urlPathEqualTo(webhookPath))).get(0).getBodyAsString();
		assertThat(body).contains("matchday-results-md-md-m1.png");
	}

	@Test
	void givenExistingRow_whenPostMatchdayResults_thenPatchAndAttachmentsAdvance() throws Exception {
		String webhookPath = "/webhooks/981/tok-md2";
		String webhookUrl = wm.baseUrl() + webhookPath;
		setForumWebhook(webhookUrl);
		Matchday md = seedMatchday("M2", true);
		wm.stubFor(post(urlPathEqualTo(webhookPath))
				.willReturn(okJson("{\"id\":\"msg-md2\",\"channel_id\":\"981\"}")));
		DiscordPost first = service.postMatchdayResults(md);

		wm.stubFor(patch(urlPathEqualTo(webhookPath + "/messages/msg-md2"))
				.willReturn(okJson("{\"id\":\"msg-md2\",\"channel_id\":\"981\"}")));

		DiscordPost second = service.postMatchdayResults(md);

		assertThat(second.getId()).isEqualTo(first.getId());
		assertThat(second.getAttachmentsReplacedAt()).isNotNull();
		wm.verify(patchRequestedFor(urlPathEqualTo(webhookPath + "/messages/msg-md2")));
	}

	@Test
	void givenNotAllFinal_whenPostMatchdayResults_thenBusinessRuleException() throws Exception {
		String webhookPath = "/webhooks/982/tok-md3";
		String webhookUrl = wm.baseUrl() + webhookPath;
		setForumWebhook(webhookUrl);
		Matchday md = seedMatchday("M3", false);

		assertThatThrownBy(() -> service.postMatchdayResults(md))
				.isInstanceOf(BusinessRuleException.class)
				.hasMessageContaining("Mark all matches as final first");
	}

	@Test
	void givenWebhook429_whenPostMatchdayResults_thenTransient() throws Exception {
		String webhookPath = "/webhooks/983/tok-md4";
		String webhookUrl = wm.baseUrl() + webhookPath;
		setForumWebhook(webhookUrl);
		Matchday md = seedMatchday("M4", true);
		wm.stubFor(post(urlPathEqualTo(webhookPath))
				.willReturn(aResponse().withStatus(429).withHeader("Retry-After", "1")
						.withBody("{\"message\":\"rate limited\"}")));

		assertThatThrownBy(() -> service.postMatchdayResults(md))
				.isInstanceOf(DiscordApiException.class)
				.satisfies(ex -> assertThat(((DiscordApiException) ex).category()).isEqualTo(Category.TRANSIENT));
	}

	@Test
	void givenWebhook401_whenPostMatchdayResults_thenAuth() throws Exception {
		String webhookPath = "/webhooks/984/tok-md5";
		String webhookUrl = wm.baseUrl() + webhookPath;
		setForumWebhook(webhookUrl);
		Matchday md = seedMatchday("M5", true);
		wm.stubFor(post(urlPathEqualTo(webhookPath))
				.willReturn(aResponse().withStatus(401).withBody("{\"message\":\"unauthorized\"}")));

		assertThatThrownBy(() -> service.postMatchdayResults(md))
				.isInstanceOf(DiscordApiException.class)
				.satisfies(ex -> assertThat(((DiscordApiException) ex).category()).isEqualTo(Category.AUTH));
	}

	@Test
	void givenWebhook404_whenPostMatchdayResults_thenNotFound() throws Exception {
		String webhookPath = "/webhooks/985/tok-md6";
		String webhookUrl = wm.baseUrl() + webhookPath;
		setForumWebhook(webhookUrl);
		Matchday md = seedMatchday("M6", true);
		wm.stubFor(post(urlPathEqualTo(webhookPath))
				.willReturn(aResponse().withStatus(404).withBody("{\"message\":\"unknown\"}")));

		assertThatThrownBy(() -> service.postMatchdayResults(md))
				.isInstanceOf(DiscordApiException.class)
				.satisfies(ex -> assertThat(((DiscordApiException) ex).category()).isEqualTo(Category.NOT_FOUND));
	}

	@Test
	void givenWebhook403MissingPermissions_whenPostMatchdayResults_thenMissingPermissionsOrAuth() throws Exception {
		String webhookPath = "/webhooks/986/tok-md7";
		String webhookUrl = wm.baseUrl() + webhookPath;
		setForumWebhook(webhookUrl);
		Matchday md = seedMatchday("M7", true);
		wm.stubFor(post(urlPathEqualTo(webhookPath))
				.willReturn(aResponse().withStatus(403)
						.withBody("{\"message\":\"missing permissions\",\"code\":50013}")));

		assertThatThrownBy(() -> service.postMatchdayResults(md))
				.isInstanceOf(DiscordApiException.class)
				.satisfies(ex -> assertThat(((DiscordApiException) ex).category())
						.isIn(Category.MISSING_PERMISSIONS, Category.AUTH));
	}
}
