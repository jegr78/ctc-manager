package org.ctc.e2e.discord.forum;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.ViewportSize;
import org.ctc.TestHelper;
import org.ctc.admin.service.ResultsGraphicService;
import org.ctc.discord.model.DiscordGlobalConfig;
import org.ctc.discord.repository.DiscordGlobalConfigRepository;
import org.ctc.discord.repository.DiscordPostRepository;
import org.ctc.domain.model.Driver;
import org.ctc.domain.model.Match;
import org.ctc.domain.model.Matchday;
import org.ctc.domain.model.Race;
import org.ctc.domain.model.RaceResult;
import org.ctc.domain.model.Season;
import org.ctc.domain.model.Team;
import org.ctc.domain.repository.MatchRepository;
import org.ctc.domain.repository.RaceResultRepository;
import org.ctc.domain.repository.SeasonRepository;
import org.ctc.e2e.PlaywrightConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@Tag("e2e")
class RaceDetailForumPostButtonE2ETest extends PlaywrightConfig {

	private static final byte[] PNG_BYTES = new byte[] {
			(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
	};
	private static final String THREAD_ID = "666666666666666666";

	@RegisterExtension
	static WireMockExtension wm = WireMockExtension.newInstance()
			.options(options().dynamicPort())
			.build();

	@DynamicPropertySource
	static void overrideDiscordConfig(DynamicPropertyRegistry registry) {
		registry.add("app.discord.base-url", () -> wm.baseUrl() + "/api/v10");
		registry.add("app.discord.bot-token", () -> "e2e-bot-token");
		registry.add("app.discord.allowed-hosts", () -> "discord.com,localhost,127.0.0.1");
		registry.add("app.discord.rate-limit.jitter-ms", () -> "0");
		registry.add("app.discord.rate-limit.fivexx-backoff-ms", () -> "10,10,10");
	}

	@Autowired
	TestHelper helper;

	@Autowired
	SeasonRepository seasonRepository;

	@Autowired
	MatchRepository matchRepository;

	@Autowired
	RaceResultRepository raceResultRepository;

	@Autowired
	DiscordGlobalConfigRepository configRepository;

	@Autowired
	DiscordPostRepository discordPostRepository;

	@MockitoBean
	ResultsGraphicService resultsGraphicService;

	@BeforeEach
	void setUp() throws Exception {
		setupPage();
		wm.resetAll();
		discordPostRepository.deleteAll();
		when(resultsGraphicService.generateResultsBytes(any(Race.class))).thenReturn(PNG_BYTES);
	}

	@AfterEach
	void tearDown() {
		teardownPage();
		discordPostRepository.deleteAll();
	}

	private Race seedRace(String suffix, boolean withResults, boolean withThreadId, String webhookPath) {
		Season season = helper.createSeason("Test_RFE_" + suffix);
		if (withThreadId) {
			season.setDiscordRaceResultsThreadId(THREAD_ID);
			seasonRepository.save(season);
		}
		Matchday md = helper.createMatchdayInRegularPhase(season, "Test-MD-" + suffix, 0);
		Team home = helper.createTeam("Test_RFE_Home_" + suffix, "T-RH" + suffix);
		Team away = helper.createTeam("Test_RFE_Away_" + suffix, "T-RA" + suffix);
		season.addTeam(home);
		season.addTeam(away);
		Match match = helper.createMatch(md, home, away);
		Race race = helper.createRace(md, match);
		if (withResults) {
			Driver driver = helper.createDriver("PSN-RFE-" + suffix, "Driver RFE-" + suffix);
			RaceResult result = new RaceResult(race, driver, 1, 1, false);
			raceResultRepository.save(result);
			race.getResults().add(result);
		}
		match.getRaces().add(race);
		matchRepository.save(match);

		DiscordGlobalConfig config = configRepository.findFirstByOrderByIdAsc();
		if (config == null) {
			config = new DiscordGlobalConfig();
		}
		if (webhookPath != null) {
			config.setRaceResultsForumWebhookUrl(wm.baseUrl() + webhookPath);
		} else {
			config.setRaceResultsForumWebhookUrl("");
		}
		configRepository.save(config);
		return race;
	}

	@Test
	void givenAllPreFlightGreen_whenLoadDetail_thenPostButtonEnabled() {
		Race race = seedRace("EN", true, true, "/webhooks/900/abc");

		page.navigate(url("/admin/races/" + race.getId()));

		assertThat(page.locator("[data-testid='post-race-result-to-forum']")).isVisible();
		assertThat(page.locator("[data-testid='post-race-result-to-forum-disabled']")).hasCount(0);
	}

	@Test
	void givenAllPreFlightGreen_whenClickPost_thenWebhookReceivesPostWithThreadId() {
		String webhookPath = "/webhooks/901/abc";
		Race race = seedRace("CK", true, true, webhookPath);
		wm.stubFor(get(urlPathEqualTo("/api/v10/channels/" + THREAD_ID))
				.willReturn(okJson("{\"id\":\"" + THREAD_ID + "\",\"name\":\"t\",\"type\":11,"
						+ "\"thread_metadata\":{\"archived\":false}}")));
		wm.stubFor(post(urlPathEqualTo(webhookPath))
				.willReturn(okJson("{\"id\":\"msg-rfe-1\",\"channel_id\":\"901\"}")));

		page.navigate(url("/admin/races/" + race.getId()));
		page.click("[data-testid='post-race-result-to-forum']");

		assertThat(page.locator(".alert-success")).containsText("Race result posted to forum-thread.");
		wm.verify(postRequestedFor(urlPathEqualTo(webhookPath))
				.withQueryParam("thread_id", equalTo(THREAD_ID)));
	}

	@Test
	void givenNoResults_whenLoadDetail_thenButtonDisabledWithResultsTooltip() {
		Race race = seedRace("NR", false, true, "/webhooks/902/abc");

		page.navigate(url("/admin/races/" + race.getId()));

		var disabled = page.locator("[data-testid='post-race-result-to-forum-disabled']");
		assertThat(disabled).isVisible();
		assertThat(disabled).hasAttribute("title", "No race results yet");
	}

	@Test
	void givenNoThread_whenLoadDetail_thenButtonDisabledWithThreadTooltip() {
		Race race = seedRace("NT", true, false, "/webhooks/903/abc");

		page.navigate(url("/admin/races/" + race.getId()));

		var disabled = page.locator("[data-testid='post-race-result-to-forum-disabled']");
		assertThat(disabled).isVisible();
		assertThat(disabled).hasAttribute("title", "Link a race-results thread first");
	}

	@Test
	void givenNoWebhook_whenLoadDetail_thenButtonDisabledWithWebhookTooltip() {
		Race race = seedRace("NW", true, true, null);

		page.navigate(url("/admin/races/" + race.getId()));

		var disabled = page.locator("[data-testid='post-race-result-to-forum-disabled']");
		assertThat(disabled).isVisible();
		assertThat(disabled).hasAttribute("title", "Configure race-results forum-webhook in Discord settings");
	}

	@Test
	void givenMobileViewport_whenLoadDetail_thenDiscordActionsClusterRenders() {
		Race race = seedRace("MV", true, true, "/webhooks/904/abc");

		try (BrowserContext mobile = browser.newContext(
				new Browser.NewContextOptions().setViewportSize(new ViewportSize(375, 667)))) {
			Page mobilePage = mobile.newPage();
			mobilePage.navigate(url("/admin/races/" + race.getId()));
			assertThat(mobilePage.locator("[data-testid='post-race-result-to-forum']")).isVisible();
		}
	}
}
