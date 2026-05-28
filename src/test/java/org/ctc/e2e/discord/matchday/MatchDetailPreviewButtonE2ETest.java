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
import org.ctc.e2e.PlaywrightConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@Tag("e2e")
class MatchDetailPreviewButtonE2ETest extends PlaywrightConfig {

	@Autowired
	DiscordPostRepository discordPostRepository;

	@Autowired
	MatchRepository matchRepository;

	@Autowired
	RaceRepository raceRepository;

	@Autowired
	RaceLineupRepository raceLineupRepository;

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

	private Match seedMatch(String suffix, boolean complete, String webhookUrl) {
		Season season = helper.createSeason("E2E Prev " + suffix);
		Matchday md = helper.createMatchdayInRegularPhase(season, "MD-EP-" + suffix, 0);
		Team home = helper.createTeam("EP Home " + suffix, "ep-h" + suffix);
		Team away = helper.createTeam("EP Away " + suffix, "ep-a" + suffix);
		Match match = helper.createMatch(md, home, away);
		Driver drv = helper.createDriver("ep-psn-" + suffix, "Drv " + suffix);

		Race race = helper.createRace(md, match);
		if (complete) {
			race.setDateTime(LocalDateTime.of(2026, 6, 1, 20, 30));
			race.setSettings(helper.completeRaceSettings(race));
			raceRepository.save(race);
			RaceLineup lu = new RaceLineup();
			lu.setRace(race);
			lu.setTeam(home);
			lu.setDriver(drv);
			raceLineupRepository.save(lu);
		}
		match.getRaces().add(race);
		match.setDiscordTeaser(complete ? "Big game" : null);
		match.setStreamLink(complete ? "https://twitch.tv/ep-" + suffix : null);
		match.setDiscordChannelId("chan-ep-" + suffix);
		match.setDiscordChannelWebhookUrl("https://discord.com/api/webhooks/200/tok-ep-" + suffix);

		setAnnouncementWebhook(webhookUrl);
		return matchRepository.save(match);
	}

	private void setAnnouncementWebhook(String webhookUrl) {
		DiscordGlobalConfig cfg = globalConfigService.getOrInitialize();
		cfg.setAnnouncementWebhookUrl(webhookUrl == null ? "" : webhookUrl);
		cfg.setVsEmojiName("CTC");
		globalConfigRepository.save(cfg);
	}

	private void seedPreviewPost(Match match) {
		DiscordPost p = new DiscordPost();
		p.setChannelId("300");
		p.setMessageId("msg-ep-" + match.getId().toString().substring(0, 8));
		p.setWebhookId("300");
		p.setWebhookToken("tok-ep");
		p.setPostType(DiscordPostType.MATCH_PREVIEW);
		p.setMatchId(match.getId());
		p.setPostedAt(LocalDateTime.now());
		discordPostRepository.save(p);
	}

	@Test
	void givenPreFlightFails_whenLoadDesktopMatchDetail_thenDisabledPreviewButtonVisible() {
		Match match = seedMatch("EPD1", false, "https://discord.com/api/webhooks/300/tok-ep-d1");
		page.navigate(url("/admin/matches/" + match.getId()));

		assertThat(page.locator("[data-testid='post-match-preview-disabled']")).isVisible();
		assertThat(page.locator("[data-testid='post-match-preview']")).hasCount(0);
		assertThat(page.locator("[data-testid='repost-match-preview']")).hasCount(0);
	}

	@Test
	void givenPreFlightPassesAndNoPost_whenLoadDesktopMatchDetail_thenPostPreviewButtonVisible() {
		Match match = seedMatch("EPD2", true, "https://discord.com/api/webhooks/300/tok-ep-d2");
		page.navigate(url("/admin/matches/" + match.getId()));

		assertThat(page.locator("[data-testid='post-match-preview']")).isVisible();
		assertThat(page.locator("[data-testid='post-match-preview-disabled']")).hasCount(0);
		assertThat(page.locator("[data-testid='repost-match-preview']")).hasCount(0);
	}

	@Test
	void givenPreviewPostExists_whenLoadDesktopMatchDetail_thenRepostButtonVisible() {
		Match match = seedMatch("EPD3", true, "https://discord.com/api/webhooks/300/tok-ep-d3");
		seedPreviewPost(match);
		page.navigate(url("/admin/matches/" + match.getId()));

		assertThat(page.locator("[data-testid='repost-match-preview']")).isVisible();
		assertThat(page.locator("[data-testid='post-match-preview']")).hasCount(0);
	}

	@Test
	void givenAnnouncementsNotConfigured_whenLoadDesktopMatchDetail_thenNoPreviewClusterVisible() {
		Match match = seedMatch("EPD4", true, "");
		page.navigate(url("/admin/matches/" + match.getId()));

		assertThat(page.locator("[data-testid='post-match-preview']")).hasCount(0);
		assertThat(page.locator("[data-testid='post-match-preview-disabled']")).hasCount(0);
		assertThat(page.locator("[data-testid='repost-match-preview']")).hasCount(0);
	}

	@Test
	void givenPreviewPostExists_whenLoadMobileViewport_thenRepostStillVisible() {
		Match match = seedMatch("EPM1", true, "https://discord.com/api/webhooks/300/tok-ep-m1");
		seedPreviewPost(match);
		try (BrowserContext mobileContext = browser.newContext(
				new Browser.NewContextOptions().setViewportSize(new ViewportSize(375, 667)))) {
			Page mobile = mobileContext.newPage();
			mobile.navigate(url("/admin/matches/" + match.getId()));

			assertThat(mobile.locator("[data-testid='repost-match-preview']")).isVisible();
		}
	}
}
