package org.ctc.e2e;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.ctc.TestHelper;
import org.ctc.domain.model.Track;
import org.ctc.domain.repository.*;
import org.ctc.domain.service.PickActivationService;
import org.ctc.domain.service.PickDisclosureService;
import org.ctc.testsupport.SitegenTestDir;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

@Tag("e2e")
@TestPropertySource(properties = "ctc.outbound.enabled=false")
class PickActivationE2ETest extends PlaywrightConfig {
    static final Path ROOT = SitegenTestDir.create("pick-activation-e2e");
    @DynamicPropertySource static void paths(DynamicPropertyRegistry properties) {
        properties.add("app.upload-dir", () -> ROOT.resolve("uploads").toString());
        properties.add("ctc.site.output-dir", () -> ROOT.resolve("site").toString());
    }
    @Autowired TestHelper helper;
    @Autowired TransactionTemplate tx;
    @Autowired SeasonRepository seasons;
    @Autowired TeamRepository teams;
    @Autowired RaceRepository races;
    @Autowired TrackRepository tracks;
    @Autowired PickActivationService activation;
    @Autowired PickDisclosureService disclosure;
    @Autowired PickPublicationAttemptRepository attempts;
    @Autowired org.ctc.domain.service.PickSessionEpoch epoch;
    @Autowired java.time.Clock clock;
    @Autowired org.ctc.discord.repository.DiscordPostRepository posts;
    @org.springframework.test.context.bean.override.mockito.MockitoBean org.ctc.discord.service.PickPublicationEvidenceService evidence;
    TestHelper.SeasonFixture fixture;
    UUID trackId;
    final String trackName = "Test_Activation_" + "Q".repeat(255 - "Test_Activation_".length());

    @BeforeEach void setUp() {
        // given
        context = browser.newContext(new Browser.NewContextOptions().setJavaScriptEnabled(false)); page = context.newPage();
        tx.executeWithoutResult(status -> {
            fixture = helper.createFullSeasonFixture("TAE" + UUID.randomUUID().toString().substring(0, 7));
            fixture.race().setDateTime(LocalDateTime.of(2035, 1, 1, 20, 0));
            var track = tracks.save(new Track(trackName)); trackId = track.getId(); fixture.race().setTrack(track);
        });
    }
    @AfterEach void cleanUp() {
        teardownPage();
        tx.executeWithoutResult(status -> {
            posts.deleteAll(posts.findAll().stream().filter(post -> fixture.match().getId().equals(post.getMatchId())).toList()); posts.flush();
            helper.deleteSeasonCascade(seasons.findById(fixture.season().getId()).orElseThrow()); seasons.flush();
            tracks.deleteById(trackId); teams.deleteAllById(List.of(fixture.homeTeam().getId(), fixture.awayTeam().getId()));
        });
    }

    @ParameterizedTest @CsvSource({"1440,1000,desktop", "390,844,mobile"})
    void givenConfiguredLegacyRaceWithoutJavascript_whenReviewingEnablingAndPausing_thenKeepExplicitChoicesAndProtectedEnrolment(int width, int height, String viewport) throws Exception {
        // given
        page.setViewportSize(width, height); page.navigate(url(path()));
        assertThat(page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("Review existing settings"))).isVisible();
        assertThat(page.getByLabel("Existing settings classification")).hasValue("");
        page.getByText("Current internal settings", new Page.GetByTextOptions().setExact(true)).click();
        capture("pick-collection-" + viewport);
        // when
        page.getByLabel("Existing settings classification").selectOption("WITHHELD");
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Enable collection")).press("Enter");
        // then
        assertThat(page.getByTestId("collection-status")).hasText("Enabled");
        org.assertj.core.api.Assertions.assertThat(disclosure.forPublic(fixture.race().getId()).trackName()).isNull();
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Pause collection")).click();
        assertThat(page.getByTestId("collection-status")).hasText("Paused");
        tx.executeWithoutResult(status -> org.assertj.core.api.Assertions.assertThat(races.findById(fixture.race().getId()).orElseThrow().isPicksManaged()).isTrue());
        org.assertj.core.api.Assertions.assertThat(disclosure.forPublic(fixture.race().getId()).trackName()).isNull();
    }

    @Test
    void givenChangedDraftAfterReviewWithoutJavascript_whenPosting_thenRetainTheSelectionAndShowTheConflict() {
        // given
        page.setViewportSize(390, 844); page.navigate(url(path()));
        page.getByLabel("Existing settings classification").selectOption("WITHHELD");
        tx.executeWithoutResult(status -> tracks.findById(trackId).orElseThrow().setName("Test activation changed privately"));
        // when
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Enable collection")).click();
        // then
        assertThat(page.getByRole(AriaRole.ALERT)).containsText("The collection review changed");
        assertThat(page.getByLabel("Existing settings classification")).hasValue("WITHHELD");
        org.assertj.core.api.Assertions.assertThat(activation.review(fixture.season().getId()).enabled()).isFalse();
        tx.executeWithoutResult(status -> org.assertj.core.api.Assertions.assertThat(races.findById(fixture.race().getId()).orElseThrow().isPicksManaged()).isFalse());
    }

    @ParameterizedTest @CsvSource({"1440,1000,desktop", "390,844,mobile"})
    void givenUncertainPublicationWithoutJavascript_whenVerifyingThroughTheNativeForm_thenPublishOnlyTheCapturedFields(int width, int height, String viewport) throws Exception {
        // given
        UUID batch = pendingPublication(); page.setViewportSize(width, height); page.navigate(url("/admin/pick-publications/" + batch));
        assertThat(page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("Publication review"))).isVisible();
        assertThat(page.locator("body")).not().containsText(trackName);
        Files.createDirectories(Path.of(".screenshots"));
        page.screenshot(new Page.ScreenshotOptions().setPath(Path.of(".screenshots/pick-publication-" + viewport + ".png")).setFullPage(true));
        Files.writeString(Path.of(".screenshots/pick-publication-" + viewport + ".html"), page.content());
        org.assertj.core.api.Assertions.assertThat(page.evaluate("document.documentElement.scrollWidth <= window.innerWidth")).isEqualTo(true);
        // when
        page.getByLabel("Discord message ID").fill("345678901234567890"); page.getByLabel("Management reason").fill("Test verified original Discord output");
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Verify and confirm")).press("Enter");
        // then
        assertThat(page.locator("body")).containsText("SUCCEEDED");
        org.assertj.core.api.Assertions.assertThat(disclosure.forPublic(fixture.race().getId()).trackName()).isEqualTo(trackName);
        org.assertj.core.api.Assertions.assertThat(disclosure.forPublic(fixture.race().getId()).carName()).isNull();
    }

    private UUID pendingPublication() {
        return tx.execute(status -> {
            var race = races.findById(fixture.race().getId()).orElseThrow(); race.setPicksManaged(true);
            race.getMatch().setDiscordChannelId("123456789012345678");
            race.getMatch().setDiscordChannelWebhookUrl("https://discord.com/api/v10/webhooks/234567890123456789/Test_e2e-private-token");
            UUID batch = UUID.randomUUID();
            var attempt = attempts.saveAndFlush(new org.ctc.domain.model.PickPublicationAttempt(new org.ctc.domain.model.PickPublicationAttempt.Capture(batch, race, 1, 0,
                    org.ctc.domain.model.PickPublicationOrigin.LEGACY, org.ctc.domain.model.PickPublicationOutput.LOBBY_SETTINGS,
                    org.ctc.domain.model.PickPublicationContent.from(race), java.util.Set.of(org.ctc.domain.model.PickDisclosureField.TRACK),
                    "test-manager", "123456789012345678", "234567890123456789", null, "a".repeat(64), "{}", epoch.managementGeneration(), clock.instant().minusSeconds(2))));
            attempt.uncertain("TEST_LOST_RESPONSE", clock.instant().minusSeconds(1)); return batch;
        });
    }
    private String path() { return "/admin/seasons/" + fixture.season().getId() + "/pick-collection"; }
    private void capture(String name) throws Exception {
        Files.createDirectories(Path.of(".screenshots"));
        page.screenshot(new Page.ScreenshotOptions().setPath(Path.of(".screenshots/" + name + ".png")).setFullPage(true));
        Files.writeString(Path.of(".screenshots/" + name + ".html"), page.content());
        org.assertj.core.api.Assertions.assertThat(page.evaluate("document.documentElement.scrollWidth <= window.innerWidth")).isEqualTo(true);
        var value = page.locator(".detail-fields dd").filter(new com.microsoft.playwright.Locator.FilterOptions().setHasText(trackName));
        org.assertj.core.api.Assertions.assertThat(value.evaluate("element => element.scrollWidth <= element.clientWidth")).isEqualTo(true);
    }
}
