package org.ctc.e2e;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.ctc.TestHelper;
import org.ctc.discord.DiscordRestClient;
import org.ctc.discord.model.DiscordGlobalConfig;
import org.ctc.discord.repository.DiscordGlobalConfigRepository;
import org.ctc.domain.picks.PickChannelPermissionSource;
import org.ctc.domain.repository.SeasonRepository;
import org.ctc.domain.repository.TeamRepository;
import org.ctc.domain.repository.TeamPickChannelBindingRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.mockito.Mockito.*;

@Tag("e2e")
@TestPropertySource(properties = "ctc.outbound.enabled=false")
class PrivatePicksE2ETest extends PlaywrightConfig {
    static final String GUILD = "100000000000000001", CHANNEL = "200000000000000002", BOT = "900000000000000009";
    @Autowired TestHelper helper;
    @Autowired TransactionTemplate tx;
    @Autowired SeasonRepository seasons;
    @Autowired TeamRepository teams;
    @Autowired TeamPickChannelBindingRepository bindings;
    @Autowired DiscordGlobalConfigRepository configs;
    @MockitoBean DiscordRestClient discord;
    @MockitoBean PickChannelPermissionSource permissions;
    TestHelper.SeasonFixture fixture;
    Long configId;
    String oldGuild;
    boolean createdConfig;

    @BeforeEach
    void createFixtureAndBrowser() throws Exception {
        // given
        context = browser.newContext(new Browser.NewContextOptions().setJavaScriptEnabled(false));
        page = context.newPage();
        tx.executeWithoutResult(status -> {
            fixture = helper.createFullSeasonFixture("TPE" + UUID.randomUUID().toString().substring(0, 7));
            var config = configs.findFirstByOrderByIdAsc(); createdConfig = config == null;
            if (config == null) config = new DiscordGlobalConfig();
            oldGuild = config.getGuildId(); config.setGuildId(GUILD); configId = configs.saveAndFlush(config).getId();
        });
        when(discord.fetchBotUser()).thenReturn(new DiscordRestClient.BotUser(BOT, "Test bot", "0"));
        when(permissions.hasCurrentPrivateAccess(GUILD, CHANNEL, BOT)).thenReturn(true);
    }

    @AfterEach
    void cleanUp() {
        teardownPage();
        tx.executeWithoutResult(status -> {
            helper.deleteSeasonCascade(seasons.findById(fixture.season().getId()).orElseThrow());
            seasons.flush();
            teams.deleteAllById(java.util.List.of(fixture.homeTeam().getId(), fixture.awayTeam().getId()));
            if (createdConfig) configs.deleteById(configId);
            else configs.findById(configId).orElseThrow().setGuildId(oldGuild);
        });
    }

    @ParameterizedTest
    @CsvSource({"1440,1000,desktop", "390,844,mobile"})
    void givenManagementSetupWithoutJavascript_whenSavingAndRemovingOnDesktopOrMobile_thenPersistExactBindingWithNativeForms(
            int width, int height, String viewport) throws Exception {
        // given
        page.setViewportSize(width, height);
        page.navigate(url("/admin/discord-config"));
        page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Private team picks")).click();
        assertThat(page.getByTestId("picks-gateway-health")).hasText("Disabled");
        assertThat(page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Register /picks for this guild"))).isDisabled();
        page.getByLabel("Season", new Page.GetByLabelOptions().setExact(true)).selectOption(fixture.season().getId().toString());
        page.getByLabel("Team", new Page.GetByLabelOptions().setExact(true)).selectOption(fixture.homeTeam().getId().toString());
        page.getByLabel("Private text channel ID").fill(CHANNEL);
        // when
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Save binding")).click();
        // then
        assertThat(page.getByText("Private team channel binding saved.", new Page.GetByTextOptions().setExact(true))).isVisible();
        var region = page.getByRole(AriaRole.REGION, new Page.GetByRoleOptions().setName("Saved private team channel bindings"));
        assertThat(region).containsText(CHANNEL);
        org.assertj.core.api.Assertions.assertThat(bindings.findByGuildIdAndChannelId(GUILD, CHANNEL)).hasSize(1);
        org.assertj.core.api.Assertions.assertThat(page.evaluate("document.documentElement.scrollWidth <= window.innerWidth")).isEqualTo(true);
        Files.createDirectories(Path.of(".screenshots"));
        page.screenshot(new Page.ScreenshotOptions().setPath(Path.of(".screenshots/private-picks-" + viewport + ".png")).setFullPage(true));
        region.getByRole(AriaRole.BUTTON).click();
        assertThat(page.getByText("Private team channel binding removed.", new Page.GetByTextOptions().setExact(true))).isVisible();
        org.assertj.core.api.Assertions.assertThat(bindings.findByGuildIdAndChannelId(GUILD, CHANNEL)).isEmpty();
        verify(discord).fetchBotUser(); verifyNoMoreInteractions(discord);
    }

    @Test
    void givenUnavailablePermissionLookup_whenSaving_thenRetainSelectionsAndShowFailureWithoutBinding() {
        // given
        when(permissions.hasCurrentPrivateAccess(GUILD, CHANNEL, BOT)).thenReturn(false);
        page.navigate(url("/admin/discord-config/picks"));
        page.getByLabel("Season", new Page.GetByLabelOptions().setExact(true)).selectOption(fixture.season().getId().toString());
        page.getByLabel("Team", new Page.GetByLabelOptions().setExact(true)).selectOption(fixture.homeTeam().getId().toString());
        page.getByLabel("Private text channel ID").fill(CHANNEL);
        // when
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Save binding")).click();
        // then
        assertThat(page.getByRole(AriaRole.ALERT)).containsText("Choose a private server text channel");
        assertThat(page.getByLabel("Season", new Page.GetByLabelOptions().setExact(true))).hasValue(fixture.season().getId().toString());
        assertThat(page.getByLabel("Team", new Page.GetByLabelOptions().setExact(true))).hasValue(fixture.homeTeam().getId().toString());
        assertThat(page.getByLabel("Private text channel ID")).hasValue(CHANNEL);
        org.assertj.core.api.Assertions.assertThat(bindings.findByGuildIdAndChannelId(GUILD, CHANNEL)).isEmpty();
    }
}
