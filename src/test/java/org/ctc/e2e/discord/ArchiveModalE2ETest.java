package org.ctc.e2e.discord;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.patch;
import static com.github.tomakehurst.wiremock.client.WireMock.patchRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.ViewportSize;
import com.microsoft.playwright.options.AriaRole;
import org.ctc.TestHelper;
import org.ctc.discord.model.DiscordGlobalConfig;
import org.ctc.discord.repository.DiscordGlobalConfigRepository;
import org.ctc.domain.model.Match;
import org.ctc.domain.model.Matchday;
import org.ctc.domain.model.Season;
import org.ctc.domain.model.Team;
import org.ctc.domain.repository.MatchRepository;
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
class ArchiveModalE2ETest extends PlaywrightConfig {

	@RegisterExtension
	static WireMockExtension wm = WireMockExtension.newInstance()
			.options(options().dynamicPort())
			.build();

	@DynamicPropertySource
	static void overrideDiscordConfig(DynamicPropertyRegistry registry) {
		registry.add("app.discord.base-url", () -> wm.baseUrl() + "/api/v10");
		registry.add("app.discord.bot-token", () -> "e2e-bot-token");
		registry.add("app.discord.allowed-hosts", () -> "discord.com,localhost,127.0.0.1");
		registry.add("app.discord.rate-limit.jitter-ms", () -> "0");
		registry.add("app.discord.rate-limit.fivexx-backoff-ms", () -> "10,10,10");
	}

	@Autowired
	DiscordGlobalConfigRepository configRepo;

	@Autowired
	MatchRepository matchRepository;

	@Autowired
	TestHelper helper;

	@BeforeEach
	void setUp() {
		setupPage();
		wm.resetAll();
		DiscordGlobalConfig cfg = configRepo.findFirstByOrderByIdAsc();
		if (cfg == null) {
			cfg = new DiscordGlobalConfig();
		}
		cfg.setGuildId("g1");
		cfg.setCurrentMatchCategoryId("cat-current");
		configRepo.save(cfg);
	}

	@AfterEach
	void tearDown() {
		teardownPage();
	}

	private Match seedMatchWithChannel(String suffix) {
		Season season = helper.createSeason("Test-Archive " + suffix);
		Matchday md = helper.createMatchdayInRegularPhase(season, "MD-" + suffix, 0);
		Team home = helper.createTeam("Test-Home " + suffix, "hm" + suffix);
		Team away = helper.createTeam("Test-Away " + suffix, "am" + suffix);
		Match match = helper.createMatch(md, home, away);
		match.setDiscordChannelId("c1");
		return matchRepository.save(match);
	}

	private void stubGuildChannelsWithTwoCategoriesHavingRoom(int year) {
		StringBuilder json = new StringBuilder("[");
		json.append("{\"id\":\"cat-1\",\"name\":\"Match Days Archive ").append(year)
				.append("\",\"type\":4,\"parent_id\":null}");
		for (int i = 0; i < 47; i++) {
			json.append(",{\"id\":\"t-").append(i).append("\",\"name\":\"chan\",\"type\":0,\"parent_id\":\"cat-1\"}");
		}
		json.append(",{\"id\":\"cat-2\",\"name\":\"Match Days Archive ").append(year)
				.append(" (2)\",\"type\":4,\"parent_id\":null}");
		for (int i = 0; i < 10; i++) {
			json.append(",{\"id\":\"u-").append(i).append("\",\"name\":\"chan\",\"type\":0,\"parent_id\":\"cat-2\"}");
		}
		json.append("]");
		wm.stubFor(get(urlPathEqualTo("/api/v10/guilds/g1/channels"))
				.willReturn(okJson(json.toString())));
	}

	private void stubGuildChannelsAllFull(int year) {
		StringBuilder json = new StringBuilder("[");
		json.append("{\"id\":\"cat-1\",\"name\":\"Match Days Archive ").append(year)
				.append("\",\"type\":4,\"parent_id\":null}");
		for (int i = 0; i < 50; i++) {
			json.append(",{\"id\":\"t-").append(i).append("\",\"name\":\"c\",\"type\":0,\"parent_id\":\"cat-1\"}");
		}
		json.append("]");
		wm.stubFor(get(urlPathEqualTo("/api/v10/guilds/g1/channels"))
				.willReturn(okJson(json.toString())));
	}

	private int currentYear() {
		return java.time.Year.now().getValue();
	}

	@Test
	void givenChannelExistsAndCategoriesAvailable_whenOpenModal_thenCategoriesAndCountsAndDefaultRadioVisible() {
		// given
		int year = currentYear();
		stubGuildChannelsWithTwoCategoriesHavingRoom(year);
		Match match = seedMatchWithChannel("A");

		// when
		page.navigate(url("/admin/matches/" + match.getId()));
		page.click("[data-testid='open-archive-modal']");

		// then — modal becomes visible, both radio buttons render with counts, num=2 is checked (highest-num with room)
		assertThat(page.locator("[name='categoryId']")).hasCount(2);
		assertThat(page.locator("label[for='cat-0']"))
				.containsText("Match Days Archive " + year);
		assertThat(page.locator("label[for='cat-1']"))
				.containsText("Match Days Archive " + year + " (2)");
		assertThat(page.locator("#cat-1")).isChecked();
        assertThat(page.locator("label[for='cat-0']")).containsText("47/50 channels");
        assertThat(page.locator("label[for='cat-1']")).containsText("10/50 channels");
		assertThat(page.locator("[data-testid='archive-confirm']")).isEnabled();
	}

	@Test
	void givenAllCategoriesFull_whenOpenModal_thenWarningBannerAndConfirmDisabled() {
		// given — all categories at 50/50 → resolver returns the categories (count==50) but defaultSelection is empty
		stubGuildChannelsAllFull(currentYear());
		Match match = seedMatchWithChannel("F");

		// when
		page.navigate(url("/admin/matches/" + match.getId()));
		page.click("[data-testid='open-archive-modal']");

		assertThat(page.locator("[name='categoryId']")).hasCount(1);
		assertThat(page.locator("[name='categoryId']").first()).not().isChecked();
        assertThat(page.locator("[name='categoryId']").first()).isDisabled();
        assertThat(page.locator("[data-testid='archive-all-full-banner']")).containsText("No archive category with space is available.");
        assertThat(page.locator("[data-testid='archive-confirm']")).isDisabled();
	}

	@Test
	void givenNoCategoriesFound_whenOpenModal_thenWarningBannerAndConfirmDisabled() {
		// given — no archive categories in the guild → resolver returns empty list
		wm.stubFor(get(urlPathEqualTo("/api/v10/guilds/g1/channels")).willReturn(okJson("[]")));
		Match match = seedMatchWithChannel("E");

		// when
		page.navigate(url("/admin/matches/" + match.getId()));
		page.click("[data-testid='open-archive-modal']");

		// then
		assertThat(page.locator("[data-testid='archive-all-full-banner']")).isVisible();
		assertThat(page.locator("[data-testid='archive-confirm']")).isDisabled();
	}

	@Test
	void givenSelectAndConfirm_whenSubmitForm_thenMoveToArchiveEndpointInvoked() {
		// given
		int year = currentYear();
		stubGuildChannelsWithTwoCategoriesHavingRoom(year);
		wm.stubFor(patch(urlPathEqualTo("/api/v10/channels/c1"))
				.willReturn(okJson("{\"id\":\"c1\",\"name\":\"md1\",\"type\":0,\"parent_id\":\"cat-2\"}")));
		Match match = seedMatchWithChannel("S");

		// when
		page.navigate(url("/admin/matches/" + match.getId()));
		page.click("[data-testid='open-archive-modal']");
		page.click("[data-testid='archive-confirm']");

		// then — server PATCH fired with the default-selected category-id
		wm.verify(patchRequestedFor(urlPathEqualTo("/api/v10/channels/c1"))
				.withRequestBody(matchingJsonPath("$.parent_id", equalTo("cat-2"))));
		assertThat(page.locator(".alert-success")).containsText("Channel moved to archive.");
	}

	@Test
	void givenMobileViewport_whenOpenModal_thenNoHorizontalOverflow() {
		// given
		int year = currentYear();
		stubGuildChannelsWithTwoCategoriesHavingRoom(year);
		Match match = seedMatchWithChannel("M");

		try (BrowserContext mobile = browser.newContext(
				new Browser.NewContextOptions().setViewportSize(new ViewportSize(375, 667)))) {
			Page mobilePage = mobile.newPage();

			// when
			mobilePage.navigate(url("/admin/matches/" + match.getId()));
			mobilePage.click("[data-testid='open-archive-modal']");

			// then
			Object scrollWidth = mobilePage.evaluate("document.body.scrollWidth");
			Object clientWidth = mobilePage.evaluate("document.body.clientWidth");
			org.assertj.core.api.Assertions.assertThat(scrollWidth).isEqualTo(clientWidth);
            org.assertj.core.api.Assertions.assertThat((Boolean) mobilePage.locator("#archiveModal").evaluate(
                    "dialog => { const bounds = dialog.getBoundingClientRect(); return bounds.left >= 0 && bounds.right <= innerWidth && bounds.height <= innerHeight; }"))
                    .isTrue();
		}
	}
    @Test
    void givenArchiveDialog_whenKeyboardDismiss_thenFocusReturnsToTheTrigger() {
        stubGuildChannelsWithTwoCategoriesHavingRoom(currentYear());
        Match match = seedMatchWithChannel("Keyboard");
        page.navigate(url("/admin/matches/" + match.getId()));
        var trigger = page.locator("[data-testid='open-archive-modal']");
        trigger.click();
        assertThat(page.getByRole(AriaRole.DIALOG, new Page.GetByRoleOptions().setName("Move channel to archive").setExact(true))).isVisible();
        assertThat(page.locator("#cat-1")).isFocused();
        page.keyboard().press("Tab");
        assertThat(page.locator("[data-testid='archive-confirm']")).isFocused();
        page.keyboard().press("Tab");
        assertThat(page.locator("[data-testid='archive-cancel']")).isFocused();
        page.keyboard().press("Tab");
        assertThat(page.locator("#cat-1")).isFocused();
        page.keyboard().press("Escape");
        assertThat(trigger).isFocused();
        assertThat(page.locator("#archiveModal")).not().isVisible();
        trigger.click();
        page.locator("[data-testid='archive-cancel']").click();
        assertThat(trigger).isFocused();
        trigger.click();
        page.locator("#cat-0").check();
        page.keyboard().press("Escape");
        trigger.click();
        assertThat(page.locator("#cat-0")).isChecked();
        page.locator("#archiveModal").click(new com.microsoft.playwright.Locator.ClickOptions().setPosition(-10, -10));
        assertThat(trigger).isFocused();
        wm.verify(0, patchRequestedFor(urlPathEqualTo("/api/v10/channels/c1")));
    }

    @Test
    void givenNoJavaScript_whenChooseArchiveCategory_thenNativeFormStillArchivesTheChannel() {
        stubGuildChannelsWithTwoCategoriesHavingRoom(currentYear());
        wm.stubFor(patch(urlPathEqualTo("/api/v10/channels/c1"))
                .willReturn(okJson("{\"id\":\"c1\",\"name\":\"md1\",\"type\":0,\"parent_id\":\"cat-1\"}")));
        Match match = seedMatchWithChannel("Native");
        try (var nativeContext = browser.newContext(new Browser.NewContextOptions().setJavaScriptEnabled(false).setViewportSize(390, 768))) {
            var nativePage = nativeContext.newPage();
            nativePage.navigate(url("/admin/matches/" + match.getId()));
            assertThat(nativePage.locator("[data-testid='open-archive-modal']")).not().isVisible();
            nativePage.locator("#archiveModal > summary").click();
            nativePage.locator("#cat-0").check();
            nativePage.locator("[data-testid='archive-confirm']").click();
            assertThat(nativePage.locator(".alert-success")).containsText("Channel moved to archive.");
            wm.verify(patchRequestedFor(urlPathEqualTo("/api/v10/channels/c1"))
                    .withRequestBody(matchingJsonPath("$.parent_id", equalTo("cat-1"))));
        }
    }

}
