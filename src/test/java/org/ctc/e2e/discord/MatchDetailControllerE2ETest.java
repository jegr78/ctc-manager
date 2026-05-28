package org.ctc.e2e.discord;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.ViewportSize;
import org.ctc.TestHelper;
import org.ctc.discord.model.DiscordGlobalConfig;
import org.ctc.discord.repository.DiscordGlobalConfigRepository;
import org.ctc.domain.model.Match;
import org.ctc.domain.model.Matchday;
import org.ctc.domain.model.Season;
import org.ctc.domain.model.Team;
import org.ctc.domain.repository.MatchRepository;
import org.ctc.domain.repository.TeamRepository;
import org.ctc.e2e.PlaywrightConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@Tag("e2e")
class MatchDetailControllerE2ETest extends PlaywrightConfig {

	@Autowired
	DiscordGlobalConfigRepository configRepo;

	@Autowired
	TeamRepository teamRepository;

	@Autowired
	MatchRepository matchRepository;

	@Autowired
	TestHelper helper;

	@BeforeEach
	void setUp() {
		setupPage();
	}

	@AfterEach
	void tearDown() {
		teardownPage();
	}

	private void seedConfig(String categoryId) {
		DiscordGlobalConfig cfg = configRepo.findFirstByOrderByIdAsc();
		if (cfg == null) {
			cfg = new DiscordGlobalConfig();
		}
		cfg.setGuildId("111111111111111111");
		cfg.setCurrentMatchCategoryId(categoryId);
		configRepo.save(cfg);
	}

	private Match seedMatch(String suffix, String homeRoleId, String awayRoleId) {
		Season season = helper.createSeason("E2E Match " + suffix);
		Matchday md = helper.createMatchdayInRegularPhase(season, "MD-E2E-" + suffix, 0);
		Team home = helper.createTeam("Home " + suffix, "he" + suffix);
		Team away = helper.createTeam("Away " + suffix, "ae" + suffix);
		home.setDiscordRoleId(homeRoleId);
		away.setDiscordRoleId(awayRoleId);
		teamRepository.save(home);
		teamRepository.save(away);
		return helper.createMatch(md, home, away);
	}

	@Test
	void givenMatchWithoutDiscord_whenLoadDetail_thenHeaderAndDiscordActionsAndScheduleAndRacesVisible() {
		// given — neither role set, no category
		seedConfig("");
		Match match = seedMatch("A", null, null);

		// when
		page.navigate(url("/admin/matches/" + match.getId()));

		// then — page header + Discord Actions panel + Schedule + Races
		assertThat(page.locator("h1")).containsText("heA");
		assertThat(page.locator("h1")).containsText("aeA");
		assertThat(page.locator("h2").first()).containsText("Discord Actions");
		assertThat(page.locator("[data-testid='create-discord-channel']")).isVisible();
		assertThat(page.locator("[data-testid='create-discord-channel']")).isDisabled();
		assertThat(page.locator("section h2").filter(
				new com.microsoft.playwright.Locator.FilterOptions().setHasText("Schedule"))).hasCount(1);
		assertThat(page.locator("section h2").filter(
				new com.microsoft.playwright.Locator.FilterOptions().setHasText("Races"))).hasCount(1);
	}

	@Test
	void givenMatchWithBothTeamRolesAndCategorySet_whenLoadDetail_thenCreateChannelEnabled() {
		// given
		seedConfig("222222222222222222");
		Match match = seedMatch("B", "100", "200");

		// when
		page.navigate(url("/admin/matches/" + match.getId()));

		// then
		assertThat(page.locator("[data-testid='create-discord-channel']")).isEnabled();
	}

	@Test
	void givenMatchWithChannel_whenLoadDetail_thenChannelBadgeAndArchiveModalTriggerVisible() {
		// given — match already has a channel
		seedConfig("222222222222222222");
		Match match = seedMatch("C", "100", "200");
		match.setDiscordChannelId("c1");
		matchRepository.save(match);

		// when
		page.navigate(url("/admin/matches/" + match.getId()));

		// then
		assertThat(page.locator("[data-testid='discord-channel-id']")).containsText("c1");
		assertThat(page.locator("[data-testid='open-archive-modal']")).isVisible();
	}

	@Test
	void givenMobileViewport_whenLoadDetail_thenNoHorizontalOverflow() {
		// given
		seedConfig("222222222222222222");
		Match match = seedMatch("M", "100", "200");

		// when — mobile viewport
		try (BrowserContext mobile = browser.newContext(
				new Browser.NewContextOptions().setViewportSize(new ViewportSize(375, 667)))) {
			Page mobilePage = mobile.newPage();
			mobilePage.navigate(url("/admin/matches/" + match.getId()));

			// then — body width matches scroll width (no horizontal overflow)
			Object scrollWidth = mobilePage.evaluate("document.body.scrollWidth");
			Object clientWidth = mobilePage.evaluate("document.body.clientWidth");
			org.assertj.core.api.Assertions.assertThat(scrollWidth).isEqualTo(clientWidth);
		}
	}
}
