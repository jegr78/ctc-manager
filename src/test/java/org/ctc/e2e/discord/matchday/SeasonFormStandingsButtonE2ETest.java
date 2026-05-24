package org.ctc.e2e.discord.matchday;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.ViewportSize;
import org.ctc.TestHelper;
import org.ctc.discord.model.DiscordGlobalConfig;
import org.ctc.discord.repository.DiscordGlobalConfigRepository;
import org.ctc.discord.repository.DiscordPostRepository;
import org.ctc.discord.service.DiscordGlobalConfigService;
import org.ctc.domain.model.PhaseLayout;
import org.ctc.domain.model.PhaseType;
import org.ctc.domain.model.Season;
import org.ctc.domain.model.SeasonPhase;
import org.ctc.domain.repository.SeasonPhaseRepository;
import org.ctc.domain.repository.SeasonRepository;
import org.ctc.e2e.PlaywrightConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@Tag("e2e")
class SeasonFormStandingsButtonE2ETest extends PlaywrightConfig {

	@Autowired
	DiscordPostRepository discordPostRepository;

	@Autowired
	SeasonRepository seasonRepository;

	@Autowired
	SeasonPhaseRepository seasonPhaseRepository;

	@Autowired
	DiscordGlobalConfigRepository globalConfigRepository;

	@Autowired
	DiscordGlobalConfigService globalConfigService;

	@Autowired
	TestHelper helper;

	@BeforeEach
	void setUp() {
		setupPage();
		discordPostRepository.deleteAll();
	}

	@AfterEach
	void tearDown() {
		teardownPage();
		discordPostRepository.deleteAll();
	}

	private Season seedSeason(String suffix, boolean threadLinked, String webhookUrl, boolean multiPhase) {
		Season season = helper.createSeason("E2EFormSt " + suffix);
		if (threadLinked) {
			season.setDiscordStandingsThreadId("e2e-thread-st-" + suffix);
		}
		Season saved = seasonRepository.save(season);
		if (multiPhase) {
			SeasonPhase playoff = new SeasonPhase(saved, PhaseType.PLAYOFF, PhaseLayout.BRACKET, 1);
			seasonPhaseRepository.save(playoff);
		}

		DiscordGlobalConfig cfg = globalConfigService.getOrInitialize();
		cfg.setStandingsForumWebhookUrl(webhookUrl == null ? "" : webhookUrl);
		cfg.setStandingsForumChannelId("forum-st-" + suffix);
		globalConfigRepository.save(cfg);
		return saved;
	}

	@Test
	void givenThreadAndWebhookAndMultiPhase_whenLoadDesktop_thenPhaseSelectAndPostButtonVisible() {
		Season season = seedSeason("F1", true, "https://discord.com/api/webhooks/800/tok-st-f1", true);
		page.navigate(url("/admin/seasons/" + season.getId() + "/edit"));

		assertThat(page.locator("[data-testid='post-standings']")).isVisible();
		assertThat(page.locator("[data-testid='post-standings-phase-select']")).isVisible();
		assertThat(page.locator("[data-testid='post-standings-phase-hidden']")).hasCount(0);
	}

	@Test
	void givenSinglePhase_whenLoadDesktop_thenHiddenInputInsteadOfSelect() {
		Season season = seedSeason("F2", true, "https://discord.com/api/webhooks/801/tok-st-f2", false);
		page.navigate(url("/admin/seasons/" + season.getId() + "/edit"));

		assertThat(page.locator("[data-testid='post-standings']")).isVisible();
		assertThat(page.locator("[data-testid='post-standings-phase-select']")).hasCount(0);
		assertThat(page.locator("[data-testid='post-standings-phase-hidden']")).hasCount(1);
	}

	@Test
	void givenNoThreadLinked_whenLoadDesktop_thenStandingsClusterHidden() {
		Season season = seedSeason("F3", false, "https://discord.com/api/webhooks/802/tok-st-f3", true);
		page.navigate(url("/admin/seasons/" + season.getId() + "/edit"));

		assertThat(page.locator("[data-testid='standings-post-cluster']")).hasCount(0);
		assertThat(page.locator("[data-testid='post-standings']")).hasCount(0);
	}

	@Test
	void givenNoWebhookConfigured_whenLoadDesktop_thenStandingsClusterHidden() {
		Season season = seedSeason("F4", true, "", true);
		page.navigate(url("/admin/seasons/" + season.getId() + "/edit"));

		assertThat(page.locator("[data-testid='standings-post-cluster']")).hasCount(0);
	}

	@Test
	void givenPhaseHasNoPriorPost_whenLoadDesktop_thenPhaseStatusListShowsNeverPosted() {
		Season season = seedSeason("F5", true, "https://discord.com/api/webhooks/803/tok-st-f5", false);
		page.navigate(url("/admin/seasons/" + season.getId() + "/edit"));

		assertThat(page.locator("[data-testid='standings-phase-status-none']").first()).isVisible();
	}

	@Test
	void givenStandingsCluster_whenLoadMobile_thenPostButtonVisible() {
		Season season = seedSeason("F6", true, "https://discord.com/api/webhooks/804/tok-st-f6", false);
		try (BrowserContext mobileContext = browser.newContext(
				new Browser.NewContextOptions().setViewportSize(new ViewportSize(375, 667)))) {
			Page mobile = mobileContext.newPage();
			mobile.navigate(url("/admin/seasons/" + season.getId() + "/edit"));

			assertThat(mobile.locator("[data-testid='post-standings']")).isVisible();
		}
	}
}
