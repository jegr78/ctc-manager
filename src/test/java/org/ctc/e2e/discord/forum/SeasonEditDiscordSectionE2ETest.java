package org.ctc.e2e.discord.forum;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.ViewportSize;
import org.ctc.TestHelper;
import org.ctc.discord.model.DiscordGlobalConfig;
import org.ctc.discord.repository.DiscordGlobalConfigRepository;
import org.ctc.domain.model.Season;
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

@Tag("e2e")
class SeasonEditDiscordSectionE2ETest extends PlaywrightConfig {

	private static final String GUILD_ID = "111111111111111111";
	private static final String FORUM_RR = "222222222222222222";
	private static final String FORUM_ST = "333333333333333333";

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
	DiscordGlobalConfigRepository configRepo;

	@Autowired
	SeasonRepository seasonRepository;

	@Autowired
	TestHelper helper;

	@BeforeEach
	void setUp() {
		setupPage();
		wm.resetAll();
		DiscordGlobalConfig cfg = configRepo.findFirstByOrderByIdAsc();
		if (cfg == null) {
			cfg = new DiscordGlobalConfig();
		}
		cfg.setGuildId(GUILD_ID);
		cfg.setRaceResultsForumChannelId(FORUM_RR);
		cfg.setStandingsForumChannelId(FORUM_ST);
		configRepo.save(cfg);
		stubForumListings();
	}

	@AfterEach
	void tearDown() {
		teardownPage();
	}

	private Season seedSeason(String name) {
		return helper.createSeason("Test-" + name);
	}

	private void stubForumListings() {
		String activePayload = """
				{
				  "threads": [
				    {"id":"pinned-rr","name":"Pinned RR","parent_id":"%s","flags":2,"last_message_id":"500"},
				    {"id":"active-rr","name":"Active RR","parent_id":"%s","flags":0,"last_message_id":"400"},
				    {"id":"pinned-st","name":"Pinned ST","parent_id":"%s","flags":2,"last_message_id":"600"},
				    {"id":"active-st","name":"Active ST","parent_id":"%s","flags":0,"last_message_id":"300"}
				  ]
				}
				""".formatted(FORUM_RR, FORUM_RR, FORUM_ST, FORUM_ST);
		wm.stubFor(get(urlPathEqualTo("/api/v10/guilds/" + GUILD_ID + "/threads/active"))
				.willReturn(okJson(activePayload)));
		wm.stubFor(get(urlPathEqualTo("/api/v10/channels/" + FORUM_RR + "/threads/archived/public"))
				.willReturn(okJson("{\"threads\":[]}")));
		wm.stubFor(get(urlPathEqualTo("/api/v10/channels/" + FORUM_ST + "/threads/archived/public"))
				.willReturn(okJson("{\"threads\":[]}")));
	}

	@Test
	void givenSeasonAndForumChannels_whenOpenRaceResultsModal_thenPinnedAutoSelected() {
		Season season = seedSeason("PinnedAuto");

		page.navigate(url("/admin/seasons/" + season.getId() + "/edit"));

		assertThat(page.locator("#discordIntegration")).isVisible();
		page.click("[data-testid='link-race-results-open']");
		assertThat(page.locator("#linkRaceResultsModal")).isVisible();
		assertThat(page.locator("input[name='threadId'][value='pinned-rr']")).isChecked();
		assertThat(page.locator("input[name='threadId'][value='active-rr']")).not().isChecked();
	}

	@Test
	void givenModalOpen_whenConfirmRaceResults_thenThreadLinked() {
		Season season = seedSeason("ConfirmLink");

		page.navigate(url("/admin/seasons/" + season.getId() + "/edit"));
		page.click("[data-testid='link-race-results-open']");
		page.click("[data-testid='link-race-results-confirm']");

		Season reloaded = seasonRepository.findById(season.getId()).orElseThrow();
		org.assertj.core.api.Assertions.assertThat(reloaded.getDiscordRaceResultsThreadId())
				.isEqualTo("pinned-rr");
		assertThat(page.locator(".alert-success")).containsText("Thread linked.");
	}

	@Test
	void givenLinkedSeason_whenClickUnlinkStandings_thenFieldCleared() {
		Season season = seedSeason("UnlinkSt");
		season.setDiscordStandingsThreadId("pinned-st");
		seasonRepository.save(season);

		page.navigate(url("/admin/seasons/" + season.getId() + "/edit"));
		page.click("[data-testid='unlink-standings']");

		Season reloaded = seasonRepository.findById(season.getId()).orElseThrow();
		org.assertj.core.api.Assertions.assertThat(reloaded.getDiscordStandingsThreadId()).isNull();
		assertThat(page.locator(".alert-success")).containsText("Thread unlinked.");
	}

	@Test
	void givenDiscordIntegrationCard_whenInspectingDom_thenNoCreateNewThreadSurface() {
		Season season = seedSeason("NoCreate");

		page.navigate(url("/admin/seasons/" + season.getId() + "/edit"));

		assertThat(page.locator("#discordIntegration")).isVisible();
		assertThat(page.locator("#discordIntegration")).not().containsText("Create new Thread");
	}

	@Test
	void givenMobileViewport_whenLoadEdit_thenDiscordCardAndModalsRender() {
		Season season = seedSeason("MobileFit");

		try (BrowserContext mobile = browser.newContext(
				new Browser.NewContextOptions().setViewportSize(new ViewportSize(375, 667)))) {
			Page mobilePage = mobile.newPage();
			mobilePage.navigate(url("/admin/seasons/" + season.getId() + "/edit"));

			assertThat(mobilePage.locator("#discordIntegration")).isVisible();
			assertThat(mobilePage.locator("[data-testid='link-race-results-open']")).isVisible();
			assertThat(mobilePage.locator("[data-testid='link-standings-open']")).isVisible();
		}
	}
}
