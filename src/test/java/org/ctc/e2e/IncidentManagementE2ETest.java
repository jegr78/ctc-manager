package org.ctc.e2e;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.ctc.TestHelper;
import org.ctc.discord.model.DiscordGlobalConfig;
import org.ctc.discord.repository.DiscordGlobalConfigRepository;
import org.ctc.domain.incidents.AuthorizedIncidentActor;
import org.ctc.domain.incidents.IncidentManagementActor;
import org.ctc.domain.incidents.IncidentReportAccess;
import org.ctc.domain.model.IncidentParty;
import org.ctc.domain.model.IncidentPartyType;
import org.ctc.domain.model.IncidentPayload;
import org.ctc.domain.repository.*;
import org.ctc.domain.service.IncidentReportService;
import org.ctc.domain.service.IncidentWindowService;
import org.ctc.testsupport.IncidentClockConfiguration;
import org.ctc.testsupport.IncidentTestClock;
import org.ctc.testsupport.SitegenTestDir;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;
import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

@Tag("e2e")
@Import(IncidentClockConfiguration.class)
@TestPropertySource(properties = "ctc.outbound.enabled=false")
class IncidentManagementE2ETest extends PlaywrightConfig {
    static final Path ROOT = SitegenTestDir.create("incident-management-e2e");
    static final Instant NOW = Instant.parse("2026-11-03T12:00:00Z");
    static final String GUILD = "100000000000000001", CHANNEL = "200000000000000002";
    @DynamicPropertySource static void paths(DynamicPropertyRegistry properties) {
        properties.add("app.upload-dir", () -> ROOT.resolve("uploads").toString());
        properties.add("ctc.site.output-dir", () -> ROOT.resolve("site").toString());
    }
    @Autowired TestHelper helper;
    @Autowired TransactionTemplate tx;
    @Autowired SeasonRepository seasons;
    @Autowired TeamRepository teams;
    @Autowired IncidentMatchContextRepository contexts;
    @Autowired IncidentReportWindowRepository windows;
    @Autowired DiscordGlobalConfigRepository configs;
    @Autowired IncidentReportService reports;
    @Autowired IncidentWindowService deadlines;
    @Autowired IncidentTestClock clock;
    @MockitoBean IncidentReportAccess access;
    @MockitoBean org.ctc.discord.DiscordRestClient discord;
    TestHelper.SeasonFixture fixture;
    Long configId;
    String oldGuild;
    boolean createdConfig;

    @BeforeEach void setUp() {
        context = browser.newContext(new Browser.NewContextOptions().setJavaScriptEnabled(false)); page = context.newPage(); clock.set(NOW);
        tx.executeWithoutResult(status -> {
            fixture = helper.createFullSeasonFixture("TIE" + UUID.randomUUID().toString().substring(0, 7));
            fixture.race().setDateTime(LocalDateTime.of(2035, 1, 1, 20, 0)); fixture.match().setDiscordChannelId(CHANNEL);
            var config = configs.findFirstByOrderByIdAsc(); createdConfig = config == null;
            if (config == null) config = new DiscordGlobalConfig();
            oldGuild = config.getGuildId(); config.setGuildId(GUILD); configId = configs.saveAndFlush(config).getId();
        });
    }
    @AfterEach void cleanUp() {
        teardownPage();
        tx.executeWithoutResult(status -> {
            if (fixture != null) {
                helper.deleteSeasonCascade(seasons.findById(fixture.season().getId()).orElseThrow()); seasons.flush();
                teams.deleteAllById(List.of(fixture.homeTeam().getId(), fixture.awayTeam().getId()));
            }
            if (configId != null) {
                if (createdConfig) configs.deleteById(configId); else configs.findById(configId).orElseThrow().setGuildId(oldGuild);
            }
        });
    }

    @ParameterizedTest
    @CsvSource({"1440,1000,desktop", "390,844,mobile"})
    void nativeBindingOpeningHistoryAndDeadlineChangeWorkWithoutJavascript(int width, int height, String viewport) throws Exception {
        page.setViewportSize(width, height); page.navigate(url("/admin/matches/" + fixture.match().getId()));
        page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Incident reports").setExact(true)).click();
        org.mockito.Mockito.clearInvocations(discord);
        openThroughNativeForms();
        UUID id = contextId(); UUID windowId = windowId(id);
        var party = new IncidentParty(IncidentPartyType.TEAM, fixture.homeTeam().getId(), "Test party " + "L".repeat(240), "300000000000000003");
        var actor = new AuthorizedIncidentActor(id, GUILD, CHANNEL, "400000000000000004", party);
        var payload = new IncidentPayload("Test drivers " + "D".repeat(200), "00:00",
                "Test <script id='incident-private-probe'>window.privateProbe=true</script> " + "private facts ".repeat(30),
                "https://example.invalid/" + "x".repeat(800));
        var saved = reports.submit(windowId, fixture.race().getId(), 1, UUID.randomUUID(), actor, payload);
        reports.edit(saved.reportId(), 1, 1, UUID.randomUUID(), actor, payload);
        var withdrawn = reports.submit(windowId, fixture.race().getId(), 1, UUID.randomUUID(), actor, payload);
        reports.withdraw(withdrawn.reportId(), 1, 1, UUID.randomUUID(), actor);
        page.reload();
        assertThat(page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(saved.caseReference()))).isVisible();
        assertThat(page.getByText("Withdrawn", new Page.GetByTextOptions().setExact(true))).isVisible();
        assertThat(page.locator("#incident-private-probe")).hasCount(0);
        assertThat(page.locator("body")).containsText(payload.getDescription());
        org.assertj.core.api.Assertions.assertThat(page.evaluate("document.documentElement.scrollWidth <= window.innerWidth")).isEqualTo(true);
        capture("incident-management-" + viewport);
        page.getByLabel("Reporting deadline in Europe/Berlin").fill("2035-01-03T20:00");
        page.getByLabel("Reason for deadline change").fill("Test explicit management adjustment");
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Change deadline").setExact(true)).press("Enter");
        assertThat(page.getByTestId("incident-confirmed-deadline")).containsText("2035-01-03 20:00 +01:00");
        tx.executeWithoutResult(status -> {
            var window = windows.findById(windowId).orElseThrow();
            org.assertj.core.api.Assertions.assertThat(window.getVersion()).isEqualTo(2);
            org.assertj.core.api.Assertions.assertThat(window.getDeadline()).isEqualTo(Instant.parse("2035-01-03T19:00:00Z"));
            org.assertj.core.api.Assertions.assertThat(window.getPlannedStartLocal()).isEqualTo(LocalDateTime.of(2035, 1, 1, 20, 0));
        });
        org.mockito.Mockito.verifyNoInteractions(discord);
    }

    @Test
    void staleNativeChangeRetainsEnteredValuesUntilExplicitReload() throws Exception {
        page.setViewportSize(390, 844); page.navigate(url("/admin/matches/" + fixture.match().getId() + "/incidents")); openThroughNativeForms();
        UUID id = contextId();
        page.getByLabel("Reporting deadline in Europe/Berlin").fill("2035-01-03T20:00");
        page.getByLabel("Reason for deadline change").fill("Test stale management reason");
        deadlines.changeDeadline(id, Instant.parse("2035-01-04T19:00:00Z"), 1, "Test other accepted change", new IncidentManagementActor("test-manager"));
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Change deadline").setExact(true)).click();
        assertThat(page.getByRole(AriaRole.ALERT)).containsText("changed");
        assertThat(page.getByLabel("Reason for deadline change")).hasValue("Test stale management reason");
        org.assertj.core.api.Assertions.assertThat(page.getByLabel("Reporting deadline in Europe/Berlin").inputValue()).startsWith("2035-01-03T20:00");
        capture("incident-management-stale-mobile");
        page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Reload current state")).click();
        org.assertj.core.api.Assertions.assertThat(page.getByLabel("Reporting deadline in Europe/Berlin").inputValue()).startsWith("2035-01-04T20:00");
        assertThat(page.getByLabel("Reason for deadline change")).isEmpty();
    }

    @Test
    void expiredNativeWorkspaceHidesDeadlineMutationControls() {
        page.setViewportSize(390, 844); page.navigate(url("/admin/matches/" + fixture.match().getId() + "/incidents")); openThroughNativeForms();
        clock.set(Instant.parse("2035-01-02T19:00:00Z")); page.reload();
        assertThat(page.getByTestId("incident-window-state")).hasText("Expired");
        assertThat(page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Change deadline").setExact(true))).hasCount(0);
        assertThat(page.locator("body")).containsText("New reports, edits and withdrawals are blocked");
    }

    private void openThroughNativeForms() {
        assertThat(page.getByLabel("Pairing channel ID")).hasValue(CHANNEL);
        org.assertj.core.api.Assertions.assertThat(page.getByLabel("Pairing channel ID").isEditable()).isFalse();
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Bind pairing").setExact(true)).press("Enter");
        assertThat(page.getByTestId("incident-window-state")).hasText("Draft");
        org.assertj.core.api.Assertions.assertThat(page.getByLabel("Reporting deadline in Europe/Berlin").inputValue()).startsWith("2035-01-02T20:00");
        assertThat(page.getByLabel("Berlin UTC offset at that deadline")).hasValue("+01:00");
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Open reporting").setExact(true)).press("Enter");
        assertThat(page.getByTestId("incident-window-state")).hasText("Open");
    }
    private UUID contextId() { return tx.execute(status -> contexts.findByMatchId(fixture.match().getId()).orElseThrow().getId()); }
    private UUID windowId(UUID id) { return tx.execute(status -> windows.findByContextId(id).orElseThrow().getId()); }
    private void capture(String name) throws Exception {
        Files.createDirectories(Path.of(".screenshots"));
        page.screenshot(new Page.ScreenshotOptions().setPath(Path.of(".screenshots/" + name + ".png")).setFullPage(true));
        Files.writeString(Path.of(".screenshots/" + name + ".html"), page.content());
    }
}
