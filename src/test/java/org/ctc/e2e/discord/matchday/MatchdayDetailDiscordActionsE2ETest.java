package org.ctc.e2e.discord.matchday;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.ViewportSize;
import java.time.LocalDateTime;
import org.ctc.TestHelper;
import org.ctc.discord.model.DiscordGlobalConfig;
import org.ctc.discord.model.DiscordPost;
import org.ctc.discord.model.DiscordPostType;
import org.ctc.discord.repository.DiscordGlobalConfigRepository;
import org.ctc.discord.repository.DiscordPostRepository;
import org.ctc.discord.service.DiscordGlobalConfigService;
import org.ctc.domain.model.Match;
import org.ctc.domain.model.Matchday;
import org.ctc.domain.model.Race;
import org.ctc.domain.model.Season;
import org.ctc.domain.model.Team;
import org.ctc.domain.repository.MatchRepository;
import org.ctc.domain.repository.MatchdayRepository;
import org.ctc.domain.repository.RaceRepository;
import org.ctc.domain.repository.SeasonRepository;
import org.ctc.e2e.PlaywrightConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@Tag("e2e")
class MatchdayDetailDiscordActionsE2ETest extends PlaywrightConfig {

	@Autowired
	DiscordPostRepository discordPostRepository;

	@Autowired
	MatchRepository matchRepository;

	@Autowired
	MatchdayRepository matchdayRepository;

	@Autowired
	RaceRepository raceRepository;

	@Autowired
	SeasonRepository seasonRepository;

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

	private Matchday seedMatchday(String suffix, boolean allFinal, boolean threadLinked, String webhookUrl) {
		Season season = helper.createSeason("E2E MD " + suffix);
		if (threadLinked) {
			season.setDiscordRaceResultsThreadId("e2e-thread-" + suffix);
		} else {
			season.setDiscordRaceResultsThreadId(null);
		}
		seasonRepository.save(season);
		Matchday md = helper.createMatchdayInRegularPhase(season, "MD-E2E-" + suffix, 0);
		Team home = helper.createTeam("E2E MD Home " + suffix, "eh" + suffix);
		Team away = helper.createTeam("E2E MD Away " + suffix, "ea" + suffix);
		Match match = helper.createMatch(md, home, away);
		Race race = helper.createRace(md, match);
		raceRepository.save(race);
		match.getRaces().add(race);
		if (allFinal) {
			match.setHomeScore(3);
			match.setAwayScore(2);
		}
		matchRepository.save(match);
		md.getMatches().add(match);
		matchdayRepository.save(md);

		DiscordGlobalConfig cfg = globalConfigService.getOrInitialize();
		cfg.setRaceResultsForumWebhookUrl(webhookUrl == null ? "" : webhookUrl);
		globalConfigRepository.save(cfg);
		return md;
	}

	private DiscordPost seedDiscordPost(Matchday md, DiscordPostType type, String messageId, String channelId) {
		DiscordPost p = new DiscordPost();
		p.setChannelId(channelId);
		p.setMessageId(messageId);
		p.setWebhookId(channelId);
		p.setWebhookToken("tok-e2e-md");
		p.setPostType(type);
		p.setMatchdayId(md.getId());
		p.setPostedAt(LocalDateTime.now());
		return discordPostRepository.save(p);
	}

	@Test
	void givenAllFinalAndThreadAndWebhook_whenLoadDesktopMatchdayDetail_thenBothButtonsEnabled() {
		Matchday md = seedMatchday("E1", true, true, "https://discord.com/api/webhooks/400/tok-e2e-1");
		page.navigate(url("/admin/matchdays/" + md.getId()));

		assertThat(page.locator("[data-testid='post-matchday-results']")).isVisible();
		assertThat(page.locator("[data-testid='post-power-rankings']")).isVisible();
		assertThat(page.locator("[data-testid='post-matchday-results-disabled']")).hasCount(0);
		assertThat(page.locator("[data-testid='post-power-rankings-disabled']")).hasCount(0);
	}

	@Test
	void givenMatchesNotFinal_whenLoadDesktopMatchdayDetail_thenResultsDisabledPowerRankingsEnabled() {
		Matchday md = seedMatchday("E2", false, true, "https://discord.com/api/webhooks/401/tok-e2e-2");
		page.navigate(url("/admin/matchdays/" + md.getId()));

		assertThat(page.locator("[data-testid='post-matchday-results-disabled']")).isVisible();
		assertThat(page.locator("[data-testid='post-matchday-results-disabled']"))
				.hasAttribute("title", "Mark all matches as final first");
		assertThat(page.locator("[data-testid='post-power-rankings']")).isVisible();
	}

	@Test
	void givenExistingMatchdayResultsPost_whenLoadDesktop_thenRepostMatchdayResultsVisible() {
		Matchday md = seedMatchday("E3", true, true, "https://discord.com/api/webhooks/402/tok-e2e-3");
		seedDiscordPost(md, DiscordPostType.MATCHDAY_OVERVIEW, "msg-e2e-md-3", "402");
		page.navigate(url("/admin/matchdays/" + md.getId()));

		assertThat(page.locator("[data-testid='repost-matchday-results']")).isVisible();
		assertThat(page.locator("[data-testid='post-matchday-results']")).hasCount(0);
	}

	@Test
	void givenExistingPowerRankingsPost_whenLoadDesktop_thenRepostPowerRankingsVisible() {
		Matchday md = seedMatchday("E4", true, true, "https://discord.com/api/webhooks/403/tok-e2e-4");
		seedDiscordPost(md, DiscordPostType.POWER_RANKINGS, "msg-e2e-pr-4", "403");
		page.navigate(url("/admin/matchdays/" + md.getId()));

		assertThat(page.locator("[data-testid='repost-power-rankings']")).isVisible();
		assertThat(page.locator("[data-testid='post-power-rankings']")).hasCount(0);
	}

	@Test
	void givenNoThreadConfigured_whenLoadDesktop_thenDiscordActionsCardHidden() {
		Matchday md = seedMatchday("E5", true, false, "https://discord.com/api/webhooks/404/tok-e2e-5");
		page.navigate(url("/admin/matchdays/" + md.getId()));

		assertThat(page.locator("[data-testid='post-matchday-results']")).hasCount(0);
		assertThat(page.locator("[data-testid='post-power-rankings']")).hasCount(0);
		assertThat(page.locator("[data-testid='post-matchday-results-disabled']")).hasCount(0);
	}

	@Test
	void givenAllConditions_whenLoadMobileViewport_thenBothButtonsVisible() {
		Matchday md = seedMatchday("E6", true, true, "https://discord.com/api/webhooks/405/tok-e2e-6");
		try (BrowserContext mobileContext = browser.newContext(
				new Browser.NewContextOptions().setViewportSize(new ViewportSize(375, 667)))) {
			Page mobile = mobileContext.newPage();
			mobile.navigate(url("/admin/matchdays/" + md.getId()));

			assertThat(mobile.locator("[data-testid='post-matchday-results']")).isVisible();
			assertThat(mobile.locator("[data-testid='post-power-rankings']")).isVisible();
		}
	}
}
