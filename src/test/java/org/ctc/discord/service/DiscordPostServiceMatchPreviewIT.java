package org.ctc.discord.service;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import org.ctc.TestHelper;
import org.ctc.admin.service.LineupGraphicService;
import org.ctc.admin.service.SettingsGraphicService;
import org.ctc.discord.exception.DiscordApiException;
import org.ctc.discord.exception.DiscordApiException.Category;
import org.ctc.discord.model.DiscordGlobalConfig;
import org.ctc.discord.model.DiscordPost;
import org.ctc.discord.model.DiscordPostType;
import org.ctc.discord.repository.DiscordGlobalConfigRepository;
import org.ctc.discord.repository.DiscordPostRepository;
import org.ctc.domain.model.Match;
import org.ctc.domain.model.Matchday;
import org.ctc.domain.model.Race;
import org.ctc.domain.model.RaceLineup;
import org.ctc.domain.model.RaceSettings;
import org.ctc.domain.model.Season;
import org.ctc.domain.model.Team;
import org.ctc.domain.repository.MatchRepository;
import org.ctc.domain.repository.RaceLineupRepository;
import org.ctc.domain.repository.RaceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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
class DiscordPostServiceMatchPreviewIT {

	private static final byte[] PNG_BYTES = new byte[] {
			(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
	};

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
	RaceRepository raceRepository;

	@Autowired
	RaceLineupRepository raceLineupRepository;

	@Autowired
	DiscordPostRepository discordPostRepository;

	@Autowired
	DiscordGlobalConfigRepository globalConfigRepository;

	@Autowired
	DiscordGlobalConfigService globalConfigService;

	@MockitoBean
	SettingsGraphicService settingsGraphicService;

	@MockitoBean
	LineupGraphicService lineupGraphicService;

	@Value("${app.upload-dir:uploads}")
	String uploadDir;

	@BeforeEach
	void resetState() throws Exception {
		wm.resetAll();
		Path dummy = Path.of(uploadDir, "preview-dummy.png").toAbsolutePath().normalize();
		Files.createDirectories(dummy.getParent());
		Files.write(dummy, PNG_BYTES);
		when(settingsGraphicService.generateSettings(any(Race.class))).thenReturn("/uploads/preview-dummy.png");
		when(lineupGraphicService.generateLineup(any(Race.class))).thenReturn("/uploads/preview-dummy.png");
	}

	private Match seedFullMatchWith2Races(String suffix, String teaser, String streamLink) {
		Season season = helper.createSeason("Preview Season " + suffix);
		Matchday md = helper.createMatchdayInRegularPhase(season, "MD-Preview-" + suffix, 0);
		Team home = helper.createTeam("Preview Home " + suffix, "PHM" + suffix);
		Team away = helper.createTeam("Preview Away " + suffix, "PAW" + suffix);
		Match match = helper.createMatch(md, home, away);

		org.ctc.domain.model.Driver driver = helper.createDriver("psn-" + suffix, "Drv " + suffix);

		Race race1 = helper.createRace(md, match);
		race1.setDateTime(LocalDateTime.of(2026, 6, 1, 20, 30));
		race1.setSettings(new RaceSettings(race1));
		raceRepository.save(race1);
		seedLineup(race1, home, driver);

		Race race2 = helper.createRace(md, match);
		race2.setDateTime(LocalDateTime.of(2026, 6, 1, 21, 30));
		race2.setSettings(new RaceSettings(race2));
		raceRepository.save(race2);
		seedLineup(race2, home, driver);

		match.getRaces().add(race1);
		match.getRaces().add(race2);
		match.setDiscordTeaser(teaser);
		match.setStreamLink(streamLink);
		return matchRepository.save(match);
	}

	private void seedLineup(Race race, Team team, org.ctc.domain.model.Driver driver) {
		RaceLineup lu = new RaceLineup();
		lu.setRace(race);
		lu.setTeam(team);
		lu.setDriver(driver);
		raceLineupRepository.save(lu);
	}

	private void setAnnouncementWebhook(String webhookUrl) {
		DiscordGlobalConfig cfg = globalConfigService.getOrInitialize();
		cfg.setAnnouncementWebhookUrl(webhookUrl);
		cfg.setVsEmojiName("CTC");
		globalConfigRepository.save(cfg);
	}

	@Test
	void givenCompleteMatch_whenPostMatchPreview_thenMultipartPostAndRowPersisted() throws Exception {
		String webhookPath = "/webhooks/930/tok-prev1";
		String webhookUrl = wm.baseUrl() + webhookPath;
		setAnnouncementWebhook(webhookUrl);
		Match match = seedFullMatchWith2Races("P1", "Big rivalry tonight!", "https://twitch.tv/foo");
		wm.stubFor(post(urlPathEqualTo(webhookPath))
				.willReturn(okJson("{\"id\":\"msg-prev1\",\"channel_id\":\"930\"}")));

		DiscordPost saved = service.postMatchPreview(match);

		assertThat(saved.getMessageId()).isEqualTo("msg-prev1");
		assertThat(saved.getPostType()).isEqualTo(DiscordPostType.MATCH_PREVIEW);
		assertThat(saved.getChannelId()).isEqualTo("930");
		assertThat(saved.getMatchId()).isEqualTo(match.getId());
		assertThat(saved.getAttachmentsReplacedAt()).isNotNull();

		var requests = wm.findAll(postRequestedFor(urlPathEqualTo(webhookPath)));
		assertThat(requests).hasSize(1);
		String body = requests.get(0).getBodyAsString();
		assertThat(body).contains("Big rivalry tonight!");
		assertThat(body).contains("# Preview Season P1");
		assertThat(body).contains("## MD-Preview-P1");
		assertThat(body).contains("### PHMP1 vs. PAWP1");
		assertThat(body).contains("- Date: <t:");
		assertThat(body).contains("- Stream: https://twitch.tv/foo");
		assertThat(body).contains("Game On! :PHMP1: :CTC: :PAWP1:");
		assertThat(body).contains("settings-md1.png");
		assertThat(body).contains("lineups-md1.png");
		assertThat(body).contains("settings-md2.png");
		assertThat(body).contains("lineups-md2.png");
	}

	@Test
	void givenNullStreamLink_whenPostMatchPreview_thenStreamRendersAsTBA() throws Exception {
		String webhookPath = "/webhooks/931/tok-prev2";
		String webhookUrl = wm.baseUrl() + webhookPath;
		setAnnouncementWebhook(webhookUrl);
		Match match = seedFullMatchWith2Races("P2", "Teaser only", null);
		wm.stubFor(post(urlPathEqualTo(webhookPath))
				.willReturn(okJson("{\"id\":\"msg-prev2\",\"channel_id\":\"931\"}")));

		service.postMatchPreview(match);

		var requests = wm.findAll(postRequestedFor(urlPathEqualTo(webhookPath)));
		assertThat(requests).hasSize(1);
		String body = requests.get(0).getBodyAsString();
		assertThat(body).contains("- Stream: TBA");
		assertThat(body).doesNotContain("- Stream: https://");
	}

	@Test
	void givenWebhook429_whenPostMatchPreview_thenThrowsTransient() throws Exception {
		String webhookPath = "/webhooks/932/tok-prev3";
		String webhookUrl = wm.baseUrl() + webhookPath;
		setAnnouncementWebhook(webhookUrl);
		Match match = seedFullMatchWith2Races("P3", "T", "https://x.tv");
		wm.stubFor(post(urlPathEqualTo(webhookPath))
				.willReturn(aResponse().withStatus(429)
						.withHeader("Retry-After", "1")
						.withBody("{\"message\":\"rate limited\"}")));

		assertThatThrownBy(() -> service.postMatchPreview(match))
				.isInstanceOf(DiscordApiException.class)
				.satisfies(ex -> assertThat(((DiscordApiException) ex).category()).isEqualTo(Category.TRANSIENT));
	}

	@Test
	void givenWebhook401_whenPostMatchPreview_thenThrowsAuth() throws Exception {
		String webhookPath = "/webhooks/933/tok-prev4";
		String webhookUrl = wm.baseUrl() + webhookPath;
		setAnnouncementWebhook(webhookUrl);
		Match match = seedFullMatchWith2Races("P4", "T", "https://x.tv");
		wm.stubFor(post(urlPathEqualTo(webhookPath))
				.willReturn(aResponse().withStatus(401).withBody("{\"message\":\"unauthorized\"}")));

		assertThatThrownBy(() -> service.postMatchPreview(match))
				.isInstanceOf(DiscordApiException.class)
				.satisfies(ex -> assertThat(((DiscordApiException) ex).category()).isEqualTo(Category.AUTH));
	}

	@Test
	void givenWebhook404_whenPostMatchPreview_thenThrowsNotFound() throws Exception {
		String webhookPath = "/webhooks/934/tok-prev5";
		String webhookUrl = wm.baseUrl() + webhookPath;
		setAnnouncementWebhook(webhookUrl);
		Match match = seedFullMatchWith2Races("P5", "T", "https://x.tv");
		wm.stubFor(post(urlPathEqualTo(webhookPath))
				.willReturn(aResponse().withStatus(404).withBody("{\"message\":\"unknown webhook\"}")));

		assertThatThrownBy(() -> service.postMatchPreview(match))
				.isInstanceOf(DiscordApiException.class)
				.satisfies(ex -> assertThat(((DiscordApiException) ex).category()).isEqualTo(Category.NOT_FOUND));
	}

	@Test
	void givenWebhook403_whenPostMatchPreview_thenThrowsMissingPermissionsOrAuth() throws Exception {
		String webhookPath = "/webhooks/935/tok-prev6";
		String webhookUrl = wm.baseUrl() + webhookPath;
		setAnnouncementWebhook(webhookUrl);
		Match match = seedFullMatchWith2Races("P6", "T", "https://x.tv");
		wm.stubFor(post(urlPathEqualTo(webhookPath))
				.willReturn(aResponse().withStatus(403)
						.withBody("{\"message\":\"missing permissions\",\"code\":50013}")));

		assertThatThrownBy(() -> service.postMatchPreview(match))
				.isInstanceOf(DiscordApiException.class)
				.satisfies(ex -> assertThat(((DiscordApiException) ex).category())
						.isIn(Category.MISSING_PERMISSIONS, Category.AUTH));
	}

	@Test
	void givenMissingTeaser_whenPostMatchPreview_thenBusinessRuleException() throws Exception {
		String webhookPath = "/webhooks/936/tok-prev7";
		String webhookUrl = wm.baseUrl() + webhookPath;
		setAnnouncementWebhook(webhookUrl);
		Match match = seedFullMatchWith2Races("P7", null, "https://x.tv");

		assertThatThrownBy(() -> service.postMatchPreview(match))
				.hasMessageContaining("Add a teaser text");
	}

	@Test
	void givenSubTeamHomeAndAway_whenPostMatchPreview_thenGameOnLineUsesParentShortNamesForEmoji() throws Exception {
		String webhookPath = "/webhooks/937/tok-prev-sub";
		String webhookUrl = wm.baseUrl() + webhookPath;
		setAnnouncementWebhook(webhookUrl);
		Match match = seedFullMatchWithSubTeams("PSUB", "Sub-team rivalry", "https://x.tv");
		wm.stubFor(post(urlPathEqualTo(webhookPath))
				.willReturn(okJson("{\"id\":\"msg-sub\",\"channel_id\":\"937\"}")));

		service.postMatchPreview(match);

		var requests = wm.findAll(postRequestedFor(urlPathEqualTo(webhookPath)));
		String body = requests.get(0).getBodyAsString();
		assertThat(body)
				.as("H3 keeps sub-team shortNames so the matchup display stays accurate")
				.contains("### PHMPSUB B vs. PAWPSUB A");
		assertThat(body)
				.as("Game On! emoji-resolution must use the PARENT team's shortName for sub-teams")
				.contains("Game On! :PHMPSUB: :CTC: :PAWPSUB:");
	}

	private Match seedFullMatchWithSubTeams(String suffix, String teaser, String streamLink) {
		Season season = helper.createSeason("Preview Season " + suffix);
		Matchday md = helper.createMatchdayInRegularPhase(season, "MD-Preview-" + suffix, 0);
		Team homeParent = helper.createTeam("Preview Home Parent " + suffix, "PHM" + suffix);
		Team home = helper.createSubTeam("Preview Home B " + suffix, "PHM" + suffix + " B", homeParent);
		Team awayParent = helper.createTeam("Preview Away Parent " + suffix, "PAW" + suffix);
		Team away = helper.createSubTeam("Preview Away A " + suffix, "PAW" + suffix + " A", awayParent);
		Match match = helper.createMatch(md, home, away);

		org.ctc.domain.model.Driver driver = helper.createDriver("psn-" + suffix, "Drv " + suffix);
		Race race1 = helper.createRace(md, match);
		race1.setDateTime(LocalDateTime.of(2026, 6, 1, 20, 30));
		race1.setSettings(new RaceSettings(race1));
		raceRepository.save(race1);
		seedLineup(race1, home, driver);
		match.getRaces().add(race1);
		match.setDiscordTeaser(teaser);
		match.setStreamLink(streamLink);
		return matchRepository.save(match);
	}
}
