package org.ctc.e2e.discord.announcement;

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
import org.ctc.e2e.PlaywrightConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@Tag("e2e")
class MatchdayDetailDiscordAnnouncementE2ETest extends PlaywrightConfig {

	private static final String ANNOUNCEMENT_WEBHOOK_URL =
			"https://discord.com/api/webhooks/900000000000000099/test-token-e2e";

	@Autowired
	DiscordPostRepository discordPostRepository;

	@Autowired
	DiscordGlobalConfigRepository globalConfigRepository;

	@Autowired
	DiscordGlobalConfigService globalConfigService;

	@Autowired
	MatchRepository matchRepository;

	@Autowired
	MatchdayRepository matchdayRepository;

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
		resetAnnouncementWebhook();
	}

	private Matchday seedMatchday(String suffix, boolean withDeadline, boolean withWeekend, boolean teamsAssigned) {
		Season season = helper.createSeason("Test-E2E Pairings " + suffix);
		Matchday md = helper.createMatchdayInRegularPhase(season, "Match Day E2E-" + suffix, 0);
		md.setPickDeadline(withDeadline ? LocalDateTime.of(2099, 1, 15, 19, 0) : null);
		md.setScheduledWeekend(withWeekend ? "22-24 May" : null);
		Team home = helper.createTeam("T-E2E Home " + suffix, "te2e-h" + suffix);
		Team away = helper.createTeam("T-E2E Away " + suffix, "te2e-a" + suffix);
		Match match = helper.createMatch(md, home, away);
		if (!teamsAssigned) {
			match.setAwayTeam(null);
		}
		matchRepository.save(match);
		md.getMatches().add(match);
		matchdayRepository.save(md);
		return md;
	}

	private void setAnnouncementWebhook(String url) {
		DiscordGlobalConfig cfg = globalConfigService.getOrInitialize();
		cfg.setAnnouncementWebhookUrl(url);
		globalConfigRepository.save(cfg);
	}

	private void resetAnnouncementWebhook() {
		setAnnouncementWebhook("");
	}

	private Matchday seedScheduleMatchday(String suffix, boolean withRaceTime) {
		Season season = helper.createSeason("Test-E2E Schedule " + suffix);
		Matchday md = helper.createMatchdayInRegularPhase(season, "Match Day SCH-" + suffix, 0);
		Team home = helper.createTeam("T-E2E SH " + suffix, "tesh-" + suffix);
		Team away = helper.createTeam("T-E2E SA " + suffix, "tesa-" + suffix);
		Match match = helper.createMatch(md, home, away);
		Race race = helper.createRace(md, match);
		if (withRaceTime) {
			race.setDateTime(LocalDateTime.of(2026, 5, 30, 19, 0));
			raceRepository.save(race);
		}
		match.getRaces().add(race);
		matchRepository.save(match);
		md.getMatches().add(match);
		return matchdayRepository.save(md);
	}

	private void seedSchedulePost(Matchday md, LocalDateTime postedAt) {
		DiscordPost p = new DiscordPost();
		p.setChannelId("900000000000000099");
		p.setMessageId("msg-sch-" + md.getId().toString().substring(0, 8));
		p.setWebhookId("900000000000000099");
		p.setWebhookToken("tok");
		p.setPostType(DiscordPostType.MATCHDAY_SCHEDULE);
		p.setMatchdayId(md.getId());
		p.setPostedAt(postedAt);
		discordPostRepository.save(p);
	}

	private void seedPairingsPost(Matchday md, LocalDateTime postedAt) {
		DiscordPost p = new DiscordPost();
		p.setChannelId("900000000000000099");
		p.setMessageId("msg-pair-" + md.getId().toString().substring(0, 8));
		p.setWebhookId("900000000000000099");
		p.setWebhookToken("tok");
		p.setPostType(DiscordPostType.MATCHDAY_PAIRINGS);
		p.setMatchdayId(md.getId());
		p.setPostedAt(postedAt);
		discordPostRepository.save(p);
	}

	@Test
	void givenNoAnnouncementWebhook_whenLoadMatchdayDetail_thenAnnouncementSectionAbsent() {
		resetAnnouncementWebhook();
		Matchday md = seedMatchday("S1", true, true, true);
		page.navigate(url("/admin/matchdays/" + md.getId()));

		assertThat(page.locator("[data-testid='post-matchday-pairings-disabled']")).hasCount(0);
		assertThat(page.locator("[data-testid='post-matchday-pairings']")).hasCount(0);
	}

	@Test
	void givenMissingDeadline_whenLoadMatchdayDetail_thenButtonDisabledWithReason() {
		setAnnouncementWebhook(ANNOUNCEMENT_WEBHOOK_URL);
		Matchday md = seedMatchday("S2", false, true, true);
		page.navigate(url("/admin/matchdays/" + md.getId()));

		var disabledBtn = page.locator("[data-testid='post-matchday-pairings-disabled']");
		assertThat(disabledBtn).isVisible();
		assertThat(disabledBtn).hasAttribute("title", "Set pick deadline first");
		assertThat(page.locator("[data-testid='post-matchday-pairings']")).hasCount(0);
	}

	@Test
	void givenAllPreFlightOkNoPost_whenLoadMatchdayDetail_thenInitialPostButtonVisible() {
		setAnnouncementWebhook(ANNOUNCEMENT_WEBHOOK_URL);
		Matchday md = seedMatchday("S3", true, true, true);
		page.navigate(url("/admin/matchdays/" + md.getId()));

		assertThat(page.locator("[data-testid='post-matchday-pairings']")).isVisible();
		assertThat(page.locator("[data-testid='post-matchday-pairings-disabled']")).hasCount(0);
	}

	@Test
	void givenFreshPost_whenLoadMatchdayDetail_thenRePostLabelVisible() {
		setAnnouncementWebhook(ANNOUNCEMENT_WEBHOOK_URL);
		Matchday md = seedMatchday("S4", true, true, true);
		seedPairingsPost(md, LocalDateTime.now().plusDays(1));
		page.navigate(url("/admin/matchdays/" + md.getId()));

		assertThat(page.locator("[data-testid='repost-matchday-pairings']")).isVisible();
		assertThat(page.locator("[data-testid='update-matchday-pairings']")).hasCount(0);
	}

	@Test
	void givenMissingRaceDateTime_whenLoadMatchdayDetail_thenScheduleButtonDisabledWithReason() {
		setAnnouncementWebhook(ANNOUNCEMENT_WEBHOOK_URL);
		Matchday md = seedScheduleMatchday("D1", false);
		page.navigate(url("/admin/matchdays/" + md.getId()));

		var disabled = page.locator("[data-testid='post-matchday-schedule-disabled']");
		assertThat(disabled).isVisible();
		assertThat(disabled).hasAttribute("title", "Set Race date+time for all matches first");
		assertThat(page.locator("[data-testid='post-matchday-schedule']")).hasCount(0);
	}

	@Test
	void givenAllPreFlightOkNoSchedulePost_whenLoadMatchdayDetail_thenSchedulePostButtonVisible() {
		setAnnouncementWebhook(ANNOUNCEMENT_WEBHOOK_URL);
		Matchday md = seedScheduleMatchday("D2", true);
		page.navigate(url("/admin/matchdays/" + md.getId()));

		assertThat(page.locator("[data-testid='post-matchday-schedule']")).isVisible();
		assertThat(page.locator("[data-testid='post-matchday-schedule-disabled']")).hasCount(0);
	}

	@Test
	void givenFreshSchedulePost_whenLoadMatchdayDetail_thenRePostScheduleLabelVisible() {
		setAnnouncementWebhook(ANNOUNCEMENT_WEBHOOK_URL);
		Matchday md = seedScheduleMatchday("D3", true);
		seedSchedulePost(md, LocalDateTime.now().plusDays(1));
		page.navigate(url("/admin/matchdays/" + md.getId()));

		assertThat(page.locator("[data-testid='repost-matchday-schedule']")).isVisible();
		assertThat(page.locator("[data-testid='update-matchday-schedule']")).hasCount(0);
	}

	@Test
	void givenStalePost_whenLoadMatchdayDetailMobile_thenUpdateLabelVisible() throws InterruptedException {
		setAnnouncementWebhook(ANNOUNCEMENT_WEBHOOK_URL);
		Matchday md = seedMatchday("S5", true, true, true);
		seedPairingsPost(md, LocalDateTime.now().minusDays(1));
		Thread.sleep(10);
		md.setScheduledWeekend("29-31 May");
		matchdayRepository.save(md);

		try (BrowserContext mobileContext = browser.newContext(
				new Browser.NewContextOptions().setViewportSize(new ViewportSize(375, 667)))) {
			Page mobile = mobileContext.newPage();
			mobile.navigate(url("/admin/matchdays/" + md.getId()));

			var update = mobile.locator("[data-testid='update-matchday-pairings']");
			assertThat(update).isVisible();
			assertThat(update).hasAttribute("title", "Matchday has been edited since last post");
		}
	}
}
