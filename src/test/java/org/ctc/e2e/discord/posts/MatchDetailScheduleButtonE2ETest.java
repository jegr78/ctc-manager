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
import org.ctc.domain.model.Match;
import org.ctc.domain.model.Matchday;
import org.ctc.domain.model.Race;
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
class MatchDetailScheduleButtonE2ETest extends PlaywrightConfig {

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

	private Match seedMatch(String suffix, boolean withDateTime) {
		Season season = helper.createSeason("E2E Sched " + suffix);
		Matchday md = helper.createMatchdayInRegularPhase(season, "MD-ESC-" + suffix, 0);
		Team home = helper.createTeam("ESC Home " + suffix, "esc-h" + suffix);
		Team away = helper.createTeam("ESC Away " + suffix, "esc-a" + suffix);
		Match match = helper.createMatch(md, home, away);
		Race race = helper.createRace(md, match);
		if (withDateTime) {
			race.setDateTime(LocalDateTime.of(2026, 6, 1, 20, 30));
			raceRepository.save(race);
		}
		match.getRaces().add(race);
		match.setDiscordChannelId("chan-esc-" + suffix);
		match.setDiscordChannelWebhookUrl("https://discord.com/api/webhooks/100/tok-esc-" + suffix);
		return matchRepository.save(match);
	}

	private void seedSchedulePost(Match match) {
		DiscordPost p = new DiscordPost();
		p.setChannelId(match.getDiscordChannelId());
		p.setMessageId("msg-esc-" + match.getId().toString().substring(0, 8));
		p.setWebhookId("100");
		p.setWebhookToken("tok");
		p.setPostType(DiscordPostType.SCHEDULE);
		p.setMatchId(match.getId());
		p.setPostedAt(LocalDateTime.now());
		discordPostRepository.save(p);
	}

	@Test
	void givenNoRaceDateTime_whenLoadDesktopMatchDetail_thenScheduleButtonDisabledWithTooltip() {
		Match match = seedMatch("E1", false);
		page.navigate(url("/admin/matches/" + match.getId()));

		var disabledBtn = page.locator("[data-testid='post-schedule-disabled']");
		assertThat(disabledBtn).isVisible();
		assertThat(disabledBtn).hasAttribute("title", "Schedule a race time first");
		assertThat(page.locator("[data-testid='post-schedule']")).hasCount(0);
	}

	@Test
	void givenRaceDateTimeSetNoPost_whenLoadDesktopMatchDetail_thenPostScheduleButtonVisible() {
		Match match = seedMatch("E2", true);
		page.navigate(url("/admin/matches/" + match.getId()));

		assertThat(page.locator("[data-testid='post-schedule']")).isVisible();
		assertThat(page.locator("[data-testid='post-schedule-disabled']")).hasCount(0);
	}

	@Test
	void givenSchedulePostExists_whenLoadDesktopMatchDetail_thenRepostButtonVisible() {
		Match match = seedMatch("E3", true);
		seedSchedulePost(match);
		page.navigate(url("/admin/matches/" + match.getId()));

		assertThat(page.locator("[data-testid='repost-schedule']")).isVisible();
		assertThat(page.locator("[data-testid='post-schedule']")).hasCount(0);
	}

	@Test
	void givenSchedulePostExists_whenLoadMobileViewport_thenRepostStillVisible() {
		Match match = seedMatch("E4", true);
		seedSchedulePost(match);
		try (BrowserContext mobileContext = browser.newContext(
				new Browser.NewContextOptions().setViewportSize(new ViewportSize(375, 667)))) {
			Page mobile = mobileContext.newPage();
			mobile.navigate(url("/admin/matches/" + match.getId()));

			assertThat(mobile.locator("[data-testid='repost-schedule']")).isVisible();
		}
	}
}
