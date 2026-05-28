package org.ctc.discord.service;

import static com.github.tomakehurst.wiremock.client.WireMock.aMultipart;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
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
import java.time.LocalDateTime;
import org.ctc.TestHelper;
import org.ctc.admin.service.MatchdayPairingsGraphicService;
import org.ctc.discord.DiscordEmojiCache;
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
class DiscordPostServiceMatchdayPairingsIT {

	private static final byte[] PNG_BYTES;

	static {
		byte[] header = new byte[] { (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A };
		byte[] payload = new byte[2048];
		System.arraycopy(header, 0, payload, 0, header.length);
		PNG_BYTES = payload;
	}

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
	MatchdayPairingsGraphicService matchdayPairingsGraphicService;

	@MockitoBean
	DiscordEmojiCache discordEmojiCache;

	@BeforeEach
	void resetState() throws Exception {
		wm.resetAll();
		discordPostRepository.deleteAll();
		DiscordGlobalConfig cfg = globalConfigService.getOrInitialize();
		cfg.setAnnouncementWebhookUrl("");
		cfg.setRaceResultsForumWebhookUrl("");
		cfg.setStandingsForumWebhookUrl("");
		cfg.setMatchdayPairingsTemplate(null);
		cfg.setVsEmojiName("CTC");
		globalConfigRepository.save(cfg);
		when(matchdayPairingsGraphicService.generatePairings(any(Matchday.class))).thenReturn(PNG_BYTES);
		when(discordEmojiCache.emojiFor("CTC")).thenReturn("<:CTC:1234567890>");
	}

	private Matchday seedMatchday(String suffix, boolean withDeadline, boolean withWeekend, boolean teamsAssigned) {
		Season season = helper.createSeason("Test-Pairings Season " + suffix);
		Matchday md = helper.createMatchdayInRegularPhase(season, "Match Day " + suffix, 0);
		md.setPickDeadline(withDeadline ? LocalDateTime.of(2099, 1, 15, 19, 0) : null);
		md.setScheduledWeekend(withWeekend ? "22-24 May" : null);
		Team home = helper.createTeam("T-Home-" + suffix, "th" + suffix);
		Team away = helper.createTeam("T-Away-" + suffix, "ta" + suffix);
		Match match = helper.createMatch(md, home, away);
		if (!teamsAssigned) {
			match.setAwayTeam(null);
		}
		matchRepository.save(match);
		md.getMatches().add(match);
		matchdayRepository.save(md);
		return md;
	}

	private void setAnnouncementWebhook(String webhookUrl) {
		DiscordGlobalConfig cfg = globalConfigService.getOrInitialize();
		cfg.setAnnouncementWebhookUrl(webhookUrl);
		globalConfigRepository.save(cfg);
	}

	private void setTemplate(String template) {
		DiscordGlobalConfig cfg = globalConfigService.getOrInitialize();
		cfg.setMatchdayPairingsTemplate(template);
		globalConfigRepository.save(cfg);
	}

	@Test
	void givenAllPreFlightOk_whenPostMatchdayPairings_thenHybridMultipartPosted() throws Exception {
		String webhookPath = "/webhooks/990/tok-mp1";
		String webhookUrl = wm.baseUrl() + webhookPath;
		setAnnouncementWebhook(webhookUrl);
		Matchday md = seedMatchday("P1", true, true, true);
		wm.stubFor(post(urlPathEqualTo(webhookPath))
				.willReturn(okJson("{\"id\":\"msg-mp1\",\"channel_id\":\"990\"}")));

		DiscordPost saved = service.postMatchdayPairings(md);

		assertThat(saved.getMessageId()).isEqualTo("msg-mp1");
		assertThat(saved.getPostType()).isEqualTo(DiscordPostType.MATCHDAY_PAIRINGS);
		assertThat(saved.getChannelId()).isEqualTo("990");
		assertThat(saved.getMatchdayId()).isEqualTo(md.getId());

		wm.verify(postRequestedFor(urlPathEqualTo(webhookPath))
				.withRequestBodyPart(aMultipart("payload_json")
						.withBody(matchingJsonPath("$.content", containing("Match Day P1 Pairings")))
						.withBody(matchingJsonPath("$.content", containing("Home Teams are on the left hand side")))
						.withBody(matchingJsonPath("$.content", containing("Deadline for the picks")))
						.withBody(matchingJsonPath("$.content", containing("Scheduled weekend for the races: 22-24 May")))
						.withBody(matchingJsonPath("$.content", containing("Game On!")))
						.build())
				.withRequestBodyPart(aMultipart("files[0]").build()));

		String body = wm.findAll(postRequestedFor(urlPathEqualTo(webhookPath))).get(0).getBodyAsString();
		assertThat(body).contains("matchday-pairings-match-day-p1.png");
		assertThat(body.length()).isGreaterThan(1024);
		assertThat(body).contains("<:CTC:1234567890>");
		assertThat(body).doesNotContain("Game On! :CTC:");
		assertThat(body).doesNotContain("{{ctcEmoji}}");
	}

	@Test
	void givenExistingPost_whenPostMatchdayPairings_thenPatchesSameMessage() throws Exception {
		String webhookPath = "/webhooks/991/tok-mp2";
		String webhookUrl = wm.baseUrl() + webhookPath;
		setAnnouncementWebhook(webhookUrl);
		Matchday md = seedMatchday("P2", true, true, true);
		wm.stubFor(post(urlPathEqualTo(webhookPath))
				.willReturn(okJson("{\"id\":\"msg-mp2\",\"channel_id\":\"991\"}")));
		DiscordPost first = service.postMatchdayPairings(md);

		wm.stubFor(patch(urlPathEqualTo(webhookPath + "/messages/msg-mp2"))
				.willReturn(okJson("{\"id\":\"msg-mp2\",\"channel_id\":\"991\"}")));

		DiscordPost second = service.postMatchdayPairings(md);

		assertThat(second.getId()).isEqualTo(first.getId());
		assertThat(second.getAttachmentsReplacedAt()).isNotNull();
		wm.verify(patchRequestedFor(urlPathEqualTo(webhookPath + "/messages/msg-mp2")));
	}

	@Test
	void givenMissingPickDeadline_whenPostMatchdayPairings_thenThrowsBusinessRuleException() {
		String webhookPath = "/webhooks/992/tok-mp3";
		setAnnouncementWebhook(wm.baseUrl() + webhookPath);
		Matchday md = seedMatchday("P3", false, true, true);

		assertThatThrownBy(() -> service.postMatchdayPairings(md))
				.isInstanceOf(BusinessRuleException.class)
				.hasMessageContaining("Set pick deadline first");
	}

	@Test
	void givenBlankAnnouncementWebhook_whenPostMatchdayPairings_thenThrowsBusinessRuleException() {
		setAnnouncementWebhook("");
		Matchday md = seedMatchday("P4", true, true, true);

		assertThatThrownBy(() -> service.postMatchdayPairings(md))
				.isInstanceOf(BusinessRuleException.class)
				.hasMessageContaining("Configure announcement-webhook in Discord settings");
	}

	@Test
	void givenOperatorTemplate_whenPostMatchdayPairings_thenResolvedAgainstOperatorTemplate() throws Exception {
		String webhookPath = "/webhooks/993/tok-mp5";
		String webhookUrl = wm.baseUrl() + webhookPath;
		setAnnouncementWebhook(webhookUrl);
		setTemplate("Custom-Header for {{matchdayNumber}}\nDeadline at {{deadline}}\nWeekend: {{weekend}}");
		Matchday md = seedMatchday("P5", true, true, true);
		wm.stubFor(post(urlPathEqualTo(webhookPath))
				.willReturn(okJson("{\"id\":\"msg-mp5\",\"channel_id\":\"993\"}")));

		service.postMatchdayPairings(md);

		wm.verify(postRequestedFor(urlPathEqualTo(webhookPath))
				.withRequestBodyPart(aMultipart("payload_json")
						.withBody(matchingJsonPath("$.content", containing("Custom-Header for Match Day P5")))
						.withBody(matchingJsonPath("$.content", containing("Weekend: 22-24 May")))
						.build()));
	}

	@Test
	void givenV15MigrationApplied_whenLoadDiscordGlobalConfig_thenMatchdayPairingsTemplateNullableRoundTrip() {
		DiscordGlobalConfig cfg = globalConfigService.getOrInitialize();
		cfg.setMatchdayPairingsTemplate(null);
		globalConfigRepository.save(cfg);
		DiscordGlobalConfig reloadedNull = globalConfigRepository.findAll().get(0);
		assertThat(reloadedNull.getMatchdayPairingsTemplate()).isNull();

		String template = "Operator template body with {{deadline}}";
		cfg.setMatchdayPairingsTemplate(template);
		globalConfigRepository.save(cfg);
		DiscordGlobalConfig reloaded = globalConfigRepository.findAll().get(0);
		assertThat(reloaded.getMatchdayPairingsTemplate()).isEqualTo(template);
	}
}
