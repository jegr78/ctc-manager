package org.ctc.e2e.discord.posts;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.ViewportSize;
import java.time.LocalDateTime;
import org.ctc.TestHelper;
import org.ctc.discord.model.DiscordPost;
import org.ctc.discord.model.DiscordPostType;
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
import org.ctc.e2e.PlaywrightConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@Tag("e2e")
class MatchDetailProvisionalButtonsE2ETest extends PlaywrightConfig {

	@Autowired
	DiscordPostRepository discordPostRepository;

	@Autowired
	MatchRepository matchRepository;

	@Autowired
	RaceResultRepository raceResultRepository;

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

	private Match seedMatchWith2Races(String suffix, int racesWithResults) {
		Season season = helper.createSeason("E2E PS " + suffix);
		Matchday md = helper.createMatchdayInRegularPhase(season, "MD-EPS-" + suffix, 0);
		Team home = helper.createTeam("EPS Home " + suffix, "eps-h" + suffix);
		Team away = helper.createTeam("EPS Away " + suffix, "eps-a" + suffix);
		Match match = helper.createMatch(md, home, away);
		Driver driver = helper.createDriver("PSN-EPS-" + suffix, "Driver EPS-" + suffix);
		for (int i = 0; i < 2; i++) {
			Race race = helper.createRace(md, match);
			if (i < racesWithResults) {
				RaceResult result = new RaceResult(race, driver, 1, 1, false);
				raceResultRepository.save(result);
				race.getResults().add(result);
			}
		}
		match.setDiscordChannelId("chan-eps-" + suffix);
		match.setDiscordChannelWebhookUrl("https://discord.com/api/webhooks/950/tok-eps-" + suffix);
		return matchRepository.save(match);
	}

	private void seedExistingProvisionalPost(Match match) {
		DiscordPost post = new DiscordPost();
		post.setChannelId(match.getDiscordChannelId());
		// MESSAGE_ID column is VARCHAR(32); use a short snowflake-shaped synthetic id.
		post.setMessageId("msg-eps-" + match.getId().toString().substring(0, 8));
		post.setWebhookId("950");
		post.setWebhookToken("tok-eps-existing");
		post.setPostType(DiscordPostType.PROVISIONAL_SCORES);
		post.setPostedAt(LocalDateTime.now());
		post.setMatchId(match.getId());
		discordPostRepository.save(post);
	}

	@Test
	void givenAtLeastOneRaceHasResults_whenLoadDesktopMatchDetail_thenPostProvisionalButtonVisible() {
		Match match = seedMatchWith2Races("D1", 1);
		page.navigate(url("/admin/matches/" + match.getId()));

		assertThat(page.locator("[data-testid='post-provisional']")).isVisible();
		assertThat(page.locator("[data-testid='post-provisional-disabled']")).hasCount(0);
		assertThat(page.locator("[data-testid='repost-provisional']")).hasCount(0);
	}

	@Test
	void givenZeroRaceResults_whenLoadDesktopMatchDetail_thenProvisionalButtonDisabledWithTooltip() {
		Match match = seedMatchWith2Races("D2", 0);
		page.navigate(url("/admin/matches/" + match.getId()));

		var disabledBtn = page.locator("[data-testid='post-provisional-disabled']");
		assertThat(disabledBtn).isVisible();
		assertThat(disabledBtn).hasAttribute("title", "Provisional needs at least one completed race");
		assertThat(page.locator("[data-testid='post-provisional']")).hasCount(0);
	}

	@Test
	void givenExistingProvisionalPost_whenLoadDesktopMatchDetail_thenRePostButtonVisible() {
		Match match = seedMatchWith2Races("D3", 2);
		seedExistingProvisionalPost(match);
		page.navigate(url("/admin/matches/" + match.getId()));

		var repostBtn = page.locator("[data-testid='repost-provisional']");
		assertThat(repostBtn).isVisible();
		assertThat(repostBtn).hasText("Re-Post Provisional Scores");
		assertThat(page.locator("[data-testid='post-provisional']")).hasCount(0);
	}

	@Test
	void givenZeroRaceResults_whenLoadMobileViewport_thenDisabledTooltipStillVisible() {
		Match match = seedMatchWith2Races("M1", 0);
		try (BrowserContext mobileContext = browser.newContext(
				new Browser.NewContextOptions().setViewportSize(new ViewportSize(375, 667)))) {
			Page mobile = mobileContext.newPage();
			mobile.navigate(url("/admin/matches/" + match.getId()));

			assertThat(mobile.locator("[data-testid='post-provisional-disabled']")).isVisible();
		}
	}
}
