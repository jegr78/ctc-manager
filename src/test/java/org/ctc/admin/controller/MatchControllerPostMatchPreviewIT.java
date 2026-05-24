package org.ctc.admin.controller;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import org.ctc.TestHelper;
import org.ctc.admin.dto.MatchPreviewPreFlightResult;
import org.ctc.admin.service.LineupGraphicService;
import org.ctc.admin.service.SettingsGraphicService;
import org.ctc.discord.model.DiscordGlobalConfig;
import org.ctc.discord.repository.DiscordGlobalConfigRepository;
import org.ctc.discord.repository.DiscordPostRepository;
import org.ctc.discord.service.DiscordGlobalConfigService;
import org.ctc.domain.model.Driver;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Tag("integration")
class MatchControllerPostMatchPreviewIT {

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
	MockMvc mockMvc;

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
		discordPostRepository.deleteAll();
		Path dummy = Path.of(uploadDir, "ctrl-preview-dummy.png").toAbsolutePath().normalize();
		Files.createDirectories(dummy.getParent());
		Files.write(dummy, PNG_BYTES);
		when(settingsGraphicService.generateSettings(any(Race.class))).thenReturn("/uploads/ctrl-preview-dummy.png");
		when(lineupGraphicService.generateLineup(any(Race.class))).thenReturn("/uploads/ctrl-preview-dummy.png");
	}

	@AfterEach
	void cleanup() {
		discordPostRepository.deleteAll();
	}

	private Match seedCompleteMatch(String suffix, String teaser, String streamLink) {
		Season season = helper.createSeason("Ctrl Season " + suffix);
		Matchday md = helper.createMatchdayInRegularPhase(season, "MD-Ctrl-" + suffix, 0);
		Team home = helper.createTeam("Ctrl Home " + suffix, "ch-" + suffix);
		Team away = helper.createTeam("Ctrl Away " + suffix, "ca-" + suffix);
		Match match = helper.createMatch(md, home, away);
		Driver drv = helper.createDriver("ctrl-psn-" + suffix, "Ctrl Drv " + suffix);

		Race race = helper.createRace(md, match);
		race.setDateTime(LocalDateTime.of(2026, 6, 1, 20, 30));
		race.setSettings(new RaceSettings(race));
		raceRepository.save(race);
		RaceLineup lu = new RaceLineup();
		lu.setRace(race);
		lu.setTeam(home);
		lu.setDriver(drv);
		raceLineupRepository.save(lu);

		match.getRaces().add(race);
		match.setDiscordTeaser(teaser);
		match.setStreamLink(streamLink);
		return matchRepository.save(match);
	}

	private void configureAnnouncementWebhook(String webhookUrl) {
		DiscordGlobalConfig cfg = globalConfigService.getOrInitialize();
		cfg.setAnnouncementWebhookUrl(webhookUrl);
		cfg.setVsEmojiName("CTC");
		globalConfigRepository.save(cfg);
	}

	@Test
	void givenCompleteMatchAndWebhookSet_whenPostMatchPreview_thenRedirectAndSuccessFlash() throws Exception {
		String webhookPath = "/webhooks/950/tok-ctrl1";
		String webhookUrl = wm.baseUrl() + webhookPath;
		configureAnnouncementWebhook(webhookUrl);
		Match match = seedCompleteMatch("C1", "Teaser", "https://twitch.tv/foo");
		wm.stubFor(WireMock.post(WireMock.urlPathEqualTo(webhookPath))
				.willReturn(okJson("{\"id\":\"msg-ctrl1\",\"channel_id\":\"950\"}")));

		mockMvc.perform(MockMvcRequestBuilders.post("/admin/matches/" + match.getId() + "/post-match-preview")
						.with(csrf()))
				.andExpect(status().is3xxRedirection())
				.andExpect(view().name("redirect:/admin/matches/" + match.getId()))
				.andExpect(flash().attribute("successMessage", "Match preview posted."));
	}

	@Test
	void givenWireMock429_whenPostMatchPreview_thenTransientFlash() throws Exception {
		String webhookPath = "/webhooks/951/tok-ctrl2";
		String webhookUrl = wm.baseUrl() + webhookPath;
		configureAnnouncementWebhook(webhookUrl);
		Match match = seedCompleteMatch("C2", "Teaser", "https://twitch.tv/foo");
		wm.stubFor(WireMock.post(WireMock.urlPathEqualTo(webhookPath))
				.willReturn(aResponse().withStatus(429)
						.withHeader("Retry-After", "1")
						.withBody("{\"message\":\"rate limited\"}")));

		mockMvc.perform(MockMvcRequestBuilders.post("/admin/matches/" + match.getId() + "/post-match-preview")
						.with(csrf()))
				.andExpect(status().is3xxRedirection())
				.andExpect(flash().attribute("errorCategory", "transient"));
	}

	@Test
	void givenWireMock401_whenPostMatchPreview_thenAuthFlash() throws Exception {
		String webhookPath = "/webhooks/952/tok-ctrl3";
		String webhookUrl = wm.baseUrl() + webhookPath;
		configureAnnouncementWebhook(webhookUrl);
		Match match = seedCompleteMatch("C3", "Teaser", "https://twitch.tv/foo");
		wm.stubFor(WireMock.post(WireMock.urlPathEqualTo(webhookPath))
				.willReturn(aResponse().withStatus(401).withBody("{\"message\":\"unauthorized\"}")));

		mockMvc.perform(MockMvcRequestBuilders.post("/admin/matches/" + match.getId() + "/post-match-preview")
						.with(csrf()))
				.andExpect(status().is3xxRedirection())
				.andExpect(flash().attribute("errorCategory", "auth"));
	}

	@Test
	void givenWireMock404_whenPostMatchPreview_thenNotFoundFlash() throws Exception {
		String webhookPath = "/webhooks/953/tok-ctrl4";
		String webhookUrl = wm.baseUrl() + webhookPath;
		configureAnnouncementWebhook(webhookUrl);
		Match match = seedCompleteMatch("C4", "Teaser", "https://twitch.tv/foo");
		wm.stubFor(WireMock.post(WireMock.urlPathEqualTo(webhookPath))
				.willReturn(aResponse().withStatus(404).withBody("{\"message\":\"unknown webhook\"}")));

		mockMvc.perform(MockMvcRequestBuilders.post("/admin/matches/" + match.getId() + "/post-match-preview")
						.with(csrf()))
				.andExpect(status().is3xxRedirection())
				.andExpect(flash().attribute("errorCategory", "not-found"));
	}

	@Test
	void givenWireMock403MissingPermissions_whenPostMatchPreview_thenMissingPermissionsOrAuthFlash() throws Exception {
		String webhookPath = "/webhooks/954/tok-ctrl5";
		String webhookUrl = wm.baseUrl() + webhookPath;
		configureAnnouncementWebhook(webhookUrl);
		Match match = seedCompleteMatch("C5", "Teaser", "https://twitch.tv/foo");
		wm.stubFor(WireMock.post(WireMock.urlPathEqualTo(webhookPath))
				.willReturn(aResponse().withStatus(403)
						.withBody("{\"message\":\"missing permissions\",\"code\":50013}")));

		mockMvc.perform(MockMvcRequestBuilders.post("/admin/matches/" + match.getId() + "/post-match-preview")
						.with(csrf()))
				.andExpect(status().is3xxRedirection())
				.andExpect(result -> {
					String cat = (String) result.getFlashMap().get("errorCategory");
					assertThat(cat).isIn("missing-permissions", "auth");
				});
	}

	@Test
	void givenAnnouncementWebhookConfigured_whenDetailGet_thenModelHasPreviewEnrichment() throws Exception {
		String webhookUrl = wm.baseUrl() + "/webhooks/955/tok-ctrl6";
		configureAnnouncementWebhook(webhookUrl);
		Match match = seedCompleteMatch("C6", "Teaser", "https://twitch.tv/foo");

		mockMvc.perform(MockMvcRequestBuilders.get("/admin/matches/" + match.getId()))
				.andExpect(status().isOk())
				.andExpect(model().attribute("discordAnnouncementsConfigured", true))
				.andExpect(model().attribute("matchPreviewPost", (Object) null))
				.andExpect(model().attribute("matchPreviewPreFlight",
						new MatchPreviewPreFlightResult(true, null)));
	}

	@Test
	void givenNoAnnouncementWebhook_whenDetailGet_thenDiscordAnnouncementsConfiguredFalse() throws Exception {
		DiscordGlobalConfig cfg = globalConfigService.getOrInitialize();
		cfg.setAnnouncementWebhookUrl("");
		globalConfigRepository.save(cfg);
		Match match = seedCompleteMatch("C7", "Teaser", null);

		mockMvc.perform(MockMvcRequestBuilders.get("/admin/matches/" + match.getId()))
				.andExpect(status().isOk())
				.andExpect(model().attribute("discordAnnouncementsConfigured", false))
				.andExpect(model().attribute("matchPreviewPost", (Object) null));
	}
}
