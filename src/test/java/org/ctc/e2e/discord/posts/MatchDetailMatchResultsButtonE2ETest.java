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
class MatchDetailMatchResultsButtonE2ETest extends PlaywrightConfig {

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

	private Match seedMatch(String suffix, boolean allHaveResults) {
		Season season = helper.createSeason("E2E MR " + suffix);
		Matchday md = helper.createMatchdayInRegularPhase(season, "MD-EMR-" + suffix, 0);
		Team home = helper.createTeam("EMR Home " + suffix, "emr-h" + suffix);
		Team away = helper.createTeam("EMR Away " + suffix, "emr-a" + suffix);
		Match match = helper.createMatch(md, home, away);
		Driver driver = helper.createDriver("PSN-EMR-" + suffix, "Driver EMR " + suffix);
		for (int i = 0; i < 2; i++) {
			Race race = helper.createRace(md, match);
			if (allHaveResults || i == 0) {
				RaceResult result = new RaceResult(race, driver, 1, 1, false);
				raceResultRepository.save(result);
				race.getResults().add(result);
			}
			match.getRaces().add(race);
		}
		match.setDiscordChannelId("chan-emr-" + suffix);
		match.setDiscordChannelWebhookUrl("https://discord.com/api/webhooks/100/tok-emr-" + suffix);
		return matchRepository.save(match);
	}

	private void seedMatchResultsPost(Match match, LocalDateTime postedAt) {
		DiscordPost p = new DiscordPost();
		p.setChannelId(match.getDiscordChannelId());
		p.setMessageId("msg-emr-" + match.getId().toString().substring(0, 8));
		p.setWebhookId("100");
		p.setWebhookToken("tok");
		p.setPostType(DiscordPostType.MATCH_RESULTS);
		p.setMatchId(match.getId());
		p.setPostedAt(postedAt);
		discordPostRepository.save(p);
	}

	@Test
	void givenIncompleteResults_whenLoadDesktopMatchDetail_thenButtonDisabledWithTooltip() {
		Match match = seedMatch("E1", false);
		page.navigate(url("/admin/matches/" + match.getId()));

		var disabledBtn = page.locator("[data-testid='post-match-results-disabled']");
		assertThat(disabledBtn).isVisible();
		assertThat(disabledBtn).hasAttribute("title", "All races must have results first");
		assertThat(page.locator("[data-testid='post-match-results']")).hasCount(0);
	}

	@Test
	void givenCompleteResultsNoPost_whenLoadDesktopMatchDetail_thenPostButtonVisible() {
		Match match = seedMatch("E2", true);
		page.navigate(url("/admin/matches/" + match.getId()));

		assertThat(page.locator("[data-testid='post-match-results']")).isVisible();
		assertThat(page.locator("[data-testid='post-match-results-disabled']")).hasCount(0);
	}

	@Test
	void givenFreshPost_whenLoadDesktopMatchDetail_thenRePostLabelVisible() {
		Match match = seedMatch("E3", true);
		seedMatchResultsPost(match, LocalDateTime.now().plusDays(1));
		page.navigate(url("/admin/matches/" + match.getId()));

		assertThat(page.locator("[data-testid='repost-match-results']")).isVisible();
		assertThat(page.locator("[data-testid='update-match-results']")).hasCount(0);
	}

	@Test
	void givenStalePost_whenLoadMobileViewport_thenUpdateLabelVisible() throws InterruptedException {
		Match match = seedMatch("E4", true);
		seedMatchResultsPost(match, LocalDateTime.now());
		Thread.sleep(10);
		RaceResult result = match.getRaces().get(0).getResults().get(0);
		result.setPosition(2);
		raceResultRepository.save(result);

		try (BrowserContext mobileContext = browser.newContext(
				new Browser.NewContextOptions().setViewportSize(new ViewportSize(375, 667)))) {
			Page mobile = mobileContext.newPage();
			mobile.navigate(url("/admin/matches/" + match.getId()));

			assertThat(mobile.locator("[data-testid='update-match-results']")).isVisible();
		}
	}
}
