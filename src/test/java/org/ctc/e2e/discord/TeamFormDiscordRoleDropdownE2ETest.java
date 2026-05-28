package org.ctc.e2e.discord;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.ViewportSize;
import java.util.List;
import java.util.UUID;
import org.ctc.discord.DiscordRoleCache;
import org.ctc.discord.dto.Role;
import org.ctc.discord.model.DiscordGlobalConfig;
import org.ctc.discord.repository.DiscordGlobalConfigRepository;
import org.ctc.domain.model.Team;
import org.ctc.domain.repository.TeamRepository;
import org.ctc.e2e.PlaywrightConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@Tag("e2e")
class TeamFormDiscordRoleDropdownE2ETest extends PlaywrightConfig {

	private static final String GUILD_ID = "111111111111111111";

	@RegisterExtension
	static WireMockExtension wm = WireMockExtension.newInstance()
			.options(options().dynamicPort())
			.build();

	@Autowired
	private DiscordGlobalConfigRepository configRepo;

	@Autowired
	private TeamRepository teamRepository;

	@Autowired
	private DiscordRoleCache roleCache;

	private UUID teamId;

	@DynamicPropertySource
	static void overrideDiscordConfig(DynamicPropertyRegistry registry) {
		registry.add("app.discord.base-url", () -> wm.baseUrl() + "/api/v10");
		registry.add("app.discord.bot-token", () -> "e2e-bot-token");
		registry.add("app.discord.allowed-hosts", () -> "discord.com,localhost,127.0.0.1");
		registry.add("app.discord.rate-limit.jitter-ms", () -> "0");
		registry.add("app.discord.rate-limit.fivexx-backoff-ms", () -> "10,10,10");
	}

	@BeforeEach
	void setUp() {
		setupPage();
		wm.resetAll();
		roleCache.refresh(List.of());
		seedGuildConfig();
		seedTestTeam();
	}

	@AfterEach
	void tearDown() {
		roleCache.refresh(List.of());
		teardownPage();
	}

	private void seedGuildConfig() {
		DiscordGlobalConfig seed = configRepo.findFirstByOrderByIdAsc();
		if (seed == null) {
			seed = new DiscordGlobalConfig();
		}
		seed.setGuildId(GUILD_ID);
		seed.setAnnouncementWebhookUrl("");
		seed.setRaceResultsForumChannelId("");
		seed.setStandingsForumChannelId("");
		seed.setVsEmojiName("CTC");
		seed.setBotApplicationId(null);
		configRepo.save(seed);
	}

	private void seedTestTeam() {
		Team team = new Team("Test-Discord-Role", "T-DR");
		teamId = teamRepository.save(team).getId();
	}

	@Test
	void givenColdCache_whenLoadTeamEditPage_thenRendersPlainTextWithBadgeWarning() {
		page.navigate(url("/admin/teams/" + teamId + "/edit"));

		assertThat(page.locator("[data-testid='discord-role-input']")).isVisible();
		assertThat(page.locator(".badge-warning")).containsText("Role cache empty");
		assertThat(page.locator("[data-testid='discord-role-dropdown']")).hasCount(0);
	}

	@Test
	void givenWarmCache_whenLoadTeamEditPage_thenRendersSearchableDropdown() {
		wm.stubFor(get(urlPathEqualTo("/api/v10/guilds/" + GUILD_ID + "/roles"))
				.willReturn(okJson("[{\"id\":\"100\",\"name\":\"Admin\",\"position\":5},"
						+ "{\"id\":\"101\",\"name\":\"Member\",\"position\":1}]")));

		// Warm the cache directly — reflects what the
		// /admin/discord-config/refresh-roles-cache operator button does in prod.
		roleCache.refresh(List.of(
				new Role("100", "Admin", 5),
				new Role("101", "Member", 1)));

		page.navigate(url("/admin/teams/" + teamId + "/edit"));

		assertThat(page.locator("[data-testid='discord-role-dropdown']")).isVisible();
		assertThat(page.locator("[data-testid='discord-role-dropdown'] .dropdown-item")).hasCount(2);
	}

	@Test
	void givenMobileViewport_whenLoadTeamEditPage_thenLayoutIsMobileCorrect() {
		try (BrowserContext mobileContext = browser.newContext(
				new com.microsoft.playwright.Browser.NewContextOptions()
						.setViewportSize(new ViewportSize(375, 667)))) {
			Page mobile = mobileContext.newPage();
			mobile.navigate(url("/admin/teams/" + teamId + "/edit"));

			assertThat(mobile.locator("h1")).containsText("Edit Team");
			assertThat(mobile.locator("[data-testid='discord-role-input']")).isVisible();
		}
	}
}
