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
class MatchDetailTeamCardsButtonsE2ETest extends PlaywrightConfig {

	@Autowired
	DiscordPostRepository discordPostRepository;

	@Autowired
	MatchRepository matchRepository;

	@Autowired
	TeamRepository teamRepository;

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

	private Match seedMatchWithoutChannel(String suffix) {
		Season season = helper.createSeason("E2E TC NoChan " + suffix);
		Matchday md = helper.createMatchdayInRegularPhase(season, "MD-TCNC-" + suffix, 0);
		Team home = helper.createTeam("E2E TCNC Home " + suffix, "tcnc-h" + suffix);
		Team away = helper.createTeam("E2E TCNC Away " + suffix, "tcnc-a" + suffix);
		return helper.createMatch(md, home, away);
	}

	private Match seedMatchWithChannel(String suffix) {
		Match match = seedMatchWithoutChannel(suffix);
		match.setDiscordChannelId("chan-e2e-" + suffix);
		match.setDiscordChannelWebhookUrl("https://discord.com/api/webhooks/500/tok-" + suffix);
		return matchRepository.save(match);
	}

	private void seedTeamCardsPost(Match match) {
		DiscordPost p = new DiscordPost();
		p.setChannelId(match.getDiscordChannelId());
		p.setMessageId("msg-" + match.getId().toString().substring(0, 8));
		p.setWebhookId("500");
		p.setWebhookToken("tok-e2e");
		p.setPostType(DiscordPostType.TEAM_CARDS);
		p.setMatchId(match.getId());
		p.setPostedAt(LocalDateTime.now());
		discordPostRepository.save(p);
	}

	@Test
	void givenNoChannel_whenLoadDesktopMatchDetail_thenNoTeamCardsButtonsVisible() {
		Match match = seedMatchWithoutChannel("NC1");
		page.navigate(url("/admin/matches/" + match.getId()));

		assertThat(page.locator("[data-testid='post-team-cards']")).hasCount(0);
		assertThat(page.locator("[data-testid='repost-team-cards']")).hasCount(0);
		assertThat(page.locator("[data-testid='refresh-team-cards']")).hasCount(0);
	}

	@Test
	void givenChannelAndNoPost_whenLoadDesktopMatchDetail_thenOnlyPostTeamCardsVisible() {
		Match match = seedMatchWithChannel("WC1");
		page.navigate(url("/admin/matches/" + match.getId()));

		assertThat(page.locator("[data-testid='post-team-cards']")).isVisible();
		assertThat(page.locator("[data-testid='repost-team-cards']")).hasCount(0);
		assertThat(page.locator("[data-testid='refresh-team-cards']")).hasCount(0);
	}

	@Test
	void givenChannelAndExistingPost_whenLoadDesktopMatchDetail_thenRepostAndRefreshVisible() {
		Match match = seedMatchWithChannel("WP1");
		seedTeamCardsPost(match);
		page.navigate(url("/admin/matches/" + match.getId()));

		assertThat(page.locator("[data-testid='post-team-cards']")).hasCount(0);
		assertThat(page.locator("[data-testid='repost-team-cards']")).isVisible();
		assertThat(page.locator("[data-testid='refresh-team-cards']")).isVisible();
	}

	@Test
	void givenChannelAndExistingPost_whenLoadMobileViewport_thenRepostAndRefreshStillVisible() {
		Match match = seedMatchWithChannel("WP2");
		seedTeamCardsPost(match);
		try (BrowserContext mobileContext = browser.newContext(
				new Browser.NewContextOptions().setViewportSize(new ViewportSize(375, 667)))) {
			Page mobile = mobileContext.newPage();
			mobile.navigate(url("/admin/matches/" + match.getId()));

			assertThat(mobile.locator("[data-testid='repost-team-cards']")).isVisible();
			assertThat(mobile.locator("[data-testid='refresh-team-cards']")).isVisible();
		}
	}
}
