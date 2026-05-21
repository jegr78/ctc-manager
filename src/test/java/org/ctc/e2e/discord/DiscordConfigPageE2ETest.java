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
import org.ctc.discord.model.DiscordGlobalConfig;
import org.ctc.discord.repository.DiscordGlobalConfigRepository;
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
class DiscordConfigPageE2ETest extends PlaywrightConfig {

	@RegisterExtension
	static WireMockExtension wm = WireMockExtension.newInstance()
			.options(options().dynamicPort())
			.build();

	@Autowired
	DiscordGlobalConfigRepository repo;

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
		resetConfigToEmptyDefaults();
	}

	private void resetConfigToEmptyDefaults() {
		repo.deleteAll();
		DiscordGlobalConfig fresh = new DiscordGlobalConfig();
		repo.save(fresh);
	}

	@AfterEach
	void tearDown() {
		teardownPage();
	}

	@Test
	void givenEmptySeedRow_whenLoadDesktopPage_thenRendersNotConfiguredBadges() {
		page.navigate(url("/admin/discord-config"));

		assertThat(page).hasURL(url("/admin/discord-config"));
		assertThat(page.locator("h1")).containsText("Discord Config");
		assertThat(page.locator(".badge-warning").first()).isVisible();
	}

	@Test
	void givenEmptySeedRow_whenLoadMobilePage_thenFormStillRenders() {
		try (BrowserContext mobileContext = browser.newContext(
				new com.microsoft.playwright.Browser.NewContextOptions()
						.setViewportSize(new ViewportSize(375, 667)))) {
			Page mobile = mobileContext.newPage();
			mobile.navigate(url("/admin/discord-config"));

			assertThat(mobile.locator("h1")).containsText("Discord Config");
			assertThat(mobile.locator("#vsEmojiName")).hasValue("CTC");
		}
	}

	@Test
	void givenFilledForm_whenSave_thenSuccessBadgeAndFieldsPersist() {
		page.navigate(url("/admin/discord-config"));

		page.fill("#guildId", "123456789012345678");
		page.fill("#vsEmojiName", "CTC");
		page.click("button:has-text('Save')");

		assertThat(page.locator(".alert-success")).containsText("Configuration saved.");
		assertThat(page.locator("#guildId")).hasValue("123456789012345678");
	}

	@Test
	void givenTestConnectionClicked_whenWireMockReturns200_thenSuccessBadgeShown() {
		wm.stubFor(get(urlPathEqualTo("/api/v10/users/@me"))
				.willReturn(okJson("{\"id\":\"42\",\"username\":\"CTC-Bot\",\"discriminator\":\"0001\"}")));

		page.navigate(url("/admin/discord-config"));
		page.click("button:has-text('Test Connection')");

		assertThat(page.locator(".alert-success")).containsText("Connected as CTC-Bot");
	}

	@Test
	void givenEmptyGuildId_whenLoadPage_thenRefreshButtonsAreDisabled() {
		page.navigate(url("/admin/discord-config"));

		assertThat(page.locator("button:has-text('Refresh Server Roles')")).isDisabled();
		assertThat(page.locator("button:has-text('Refresh Emoji Cache')")).isDisabled();
		assertThat(page.locator("button:has-text('Test Announcement Webhook')")).isDisabled();
	}
}
