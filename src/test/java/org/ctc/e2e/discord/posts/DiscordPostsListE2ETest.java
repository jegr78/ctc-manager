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
import org.ctc.e2e.PlaywrightConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@Tag("e2e")
class DiscordPostsListE2ETest extends PlaywrightConfig {

	@Autowired
	DiscordPostRepository repo;

	@Autowired
	TestHelper helper;

	@BeforeEach
	void setUp() {
		setupPage();
		repo.deleteAll();
		seedThreePosts();
	}

	@AfterEach
	void tearDown() {
		teardownPage();
		repo.deleteAll();
	}

	private void seedThreePosts() {
		Season s = helper.createSeason("E2E Posts Season");
		Matchday md = helper.createMatchdayInRegularPhase(s, "MD-E2E-1", 0);
		Team home = helper.createTeam("E2E Home", "e2e-h");
		Team away = helper.createTeam("E2E Away", "e2e-a");
		Match match = helper.createMatch(md, home, away);

		repo.save(buildPost("chan-1", "msg-1", DiscordPostType.TEAM_CARDS, match.getId(), s.getId()));
		repo.save(buildPost("chan-1", "msg-2", DiscordPostType.SCHEDULE, match.getId(), s.getId()));
		repo.save(buildPost("chan-1", "msg-3", DiscordPostType.MATCH_RESULTS, match.getId(), s.getId()));
	}

	private static DiscordPost buildPost(
			String channelId, String messageId, DiscordPostType type,
			java.util.UUID matchId, java.util.UUID seasonId) {
		DiscordPost p = new DiscordPost();
		p.setChannelId(channelId);
		p.setMessageId(messageId);
		p.setWebhookId("100");
		p.setWebhookToken("tok-" + messageId);
		p.setPostType(type);
		p.setMatchId(matchId);
		p.setSeasonId(seasonId);
		p.setPostedAt(LocalDateTime.now());
		return p;
	}

	@Test
	void givenThreeSeededPosts_whenLoadDesktopList_thenAllRowsRenderAndSidebarHighlights() {
		page.navigate(url("/admin/discord/posts"));

		assertThat(page).hasURL(url("/admin/discord/posts"));
		assertThat(page.locator("h1")).containsText("Discord Posts");
		assertThat(page.locator("#discordPostsTable tbody tr")).hasCount(3);
		assertThat(page.locator(".sidebar-group a.active")).containsText("Discord Posts");
	}

	@Test
	void givenThreeSeededPosts_whenFilterByScheduleType_thenOnlyOneRowVisible() {
		page.navigate(url("/admin/discord/posts"));

		page.selectOption("#postType", "SCHEDULE");
		page.locator("button[type=submit]").click();

		assertThat(page.locator("#discordPostsTable tbody tr")).hasCount(1);
		assertThat(page.locator("#discordPostsTable tbody tr td").first()).containsText("SCHEDULE");
	}

	@Test
	void givenThreeSeededPosts_whenLoadMobileViewport_thenListStillRendersAll() {
		try (BrowserContext mobileContext = browser.newContext(
				new Browser.NewContextOptions().setViewportSize(new ViewportSize(375, 667)))) {
			Page mobile = mobileContext.newPage();
			mobile.navigate(url("/admin/discord/posts"));

			assertThat(mobile.locator("h1")).containsText("Discord Posts");
			assertThat(mobile.locator("#discordPostsTable tbody tr")).hasCount(3);
		}
	}
}
