package org.ctc.e2e.discord.posts;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.ViewportSize;
import org.ctc.TestHelper;
import org.ctc.discord.repository.DiscordPostRepository;
import org.ctc.domain.model.Match;
import org.ctc.domain.model.Matchday;
import org.ctc.domain.model.Race;
import org.ctc.domain.model.RaceSettings;
import org.ctc.domain.model.Season;
import org.ctc.domain.model.Team;
import org.ctc.domain.repository.MatchRepository;
import org.ctc.domain.repository.RaceRepository;
import org.ctc.e2e.PlaywrightConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@Tag("e2e")
class MatchDetailSettingsLineupsButtonsE2ETest extends PlaywrightConfig {

	@Autowired
	DiscordPostRepository discordPostRepository;

	@Autowired
	MatchRepository matchRepository;

	@Autowired
	RaceRepository raceRepository;

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

	private Match seedMatchWith2Races(String suffix, boolean allHaveSettings) {
		Season season = helper.createSeason("E2E SL " + suffix);
		Matchday md = helper.createMatchdayInRegularPhase(season, "MD-ESL-" + suffix, 0);
		Team home = helper.createTeam("ESL Home " + suffix, "esl-h" + suffix);
		Team away = helper.createTeam("ESL Away " + suffix, "esl-a" + suffix);
		Match match = helper.createMatch(md, home, away);
		for (int i = 0; i < 2; i++) {
			Race race = helper.createRace(md, match);
			if (allHaveSettings || i == 0) {
				race.setSettings(new RaceSettings(race));
				raceRepository.save(race);
			}
		}
		match.setDiscordChannelId("chan-esl-" + suffix);
		match.setDiscordChannelWebhookUrl("https://discord.com/api/webhooks/900/tok-" + suffix);
		return matchRepository.save(match);
	}

	@Test
	void givenIncompleteSettings_whenLoadDesktopMatchDetail_thenSettingsButtonDisabledWithTooltip() {
		Match match = seedMatchWith2Races("D1", false);
		page.navigate(url("/admin/matches/" + match.getId()));

		var disabledBtn = page.locator("[data-testid='post-settings-disabled']");
		assertThat(disabledBtn).isVisible();
		assertThat(disabledBtn).hasAttribute("title", "Configure settings for all races first");
		assertThat(page.locator("[data-testid='post-settings']")).hasCount(0);
	}

	@Test
	void givenCompleteSettingsNoPost_whenLoadDesktopMatchDetail_thenPostSettingsButtonVisible() {
		Match match = seedMatchWith2Races("D2", true);
		page.navigate(url("/admin/matches/" + match.getId()));

		assertThat(page.locator("[data-testid='post-settings']")).isVisible();
		assertThat(page.locator("[data-testid='post-settings-disabled']")).hasCount(0);
		assertThat(page.locator("[data-testid='repost-settings']")).hasCount(0);
	}

	@Test
	void givenIncompleteLineups_whenLoadDesktopMatchDetail_thenLineupsButtonDisabledWithTooltip() {
		Match match = seedMatchWith2Races("D3", true);
		page.navigate(url("/admin/matches/" + match.getId()));

		var disabledBtn = page.locator("[data-testid='post-lineups-disabled']");
		assertThat(disabledBtn).isVisible();
		assertThat(disabledBtn).hasAttribute("title", "Configure lineups for all races first");
	}

	@Test
	void givenIncompleteSettings_whenLoadMobileViewport_thenDisabledTooltipStillVisible() {
		Match match = seedMatchWith2Races("M1", false);
		try (BrowserContext mobileContext = browser.newContext(
				new Browser.NewContextOptions().setViewportSize(new ViewportSize(375, 667)))) {
			Page mobile = mobileContext.newPage();
			mobile.navigate(url("/admin/matches/" + match.getId()));

			assertThat(mobile.locator("[data-testid='post-settings-disabled']")).isVisible();
		}
	}
}
