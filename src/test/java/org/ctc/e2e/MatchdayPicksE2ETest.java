package org.ctc.e2e;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.ctc.TestHelper;
import org.ctc.admin.service.MatchdayPickAdminService;
import org.ctc.domain.model.*;
import org.ctc.domain.picks.AuthorizedPickActor;
import org.ctc.domain.picks.ManagementPickActor;
import org.ctc.domain.repository.*;
import org.ctc.domain.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

@Tag("e2e")
@TestPropertySource(properties = "ctc.outbound.enabled=false")
class MatchdayPicksE2ETest extends PlaywrightConfig {
    @Autowired TestHelper helper;
    @Autowired TransactionTemplate tx;
    @Autowired SeasonRepository seasons;
    @Autowired TeamRepository teams;
    @Autowired RaceRepository races;
    @Autowired CarRepository cars;
    @Autowired TrackRepository tracks;
    @Autowired SeasonCarPoolEntryRepository entries;
    @Autowired PickSubmissionService submissions;
    @Autowired PickReviewService reviews;
    @Autowired MatchdayPickAdminService admin;
    @Autowired SeasonCarPoolService pools;
    @Autowired org.ctc.admin.service.LobbySettingsGraphicService lobbyGraphics;
    TestHelper.SeasonFixture fixture;
    PickPayload payload;
    final List<UUID> carIds = new ArrayList<>();


    @ParameterizedTest
    @CsvSource({"1440,1000,desktop", "390,844,mobile"})
    void givenMatchdayReviewEntryWithoutJavascript_whenFilteringAndReturning_thenKeepTheSelectedMatchday(
            int width, int height, String viewport) throws Exception {
        // given
        page.setViewportSize(width, height);
        String matchdayPath = "/admin/matchdays/" + fixture.matchday().getId();
        page.navigate(url(matchdayPath)); capture("pick-deadline-matchday-" + viewport);
        assertThat(page.locator("#matchday-picks")).containsText("Europe/Berlin");
        page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Manage picks and deadline").setExact(true)).click();
        assertThat(page.locator("#pickDeadline")).hasValue("2035-01-01T12:00");
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Save picks settings and event window")).click();
        assertThat(page).hasURL(url(matchdayPath));
        // when
        page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Review team picks").setExact(true)).click();
        page.getByLabel("Submission state", new Page.GetByLabelOptions().setExact(true)).selectOption("MISSING");
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Filter picks").setExact(true)).click();
        // then
        var back = page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Back to matchday").setExact(true));
        assertThat(back).hasAttribute("href", matchdayPath);
        back.focus(); assertThat(back).isFocused(); capture("pick-review-return-" + viewport);
        back.press("Enter"); assertThat(page).hasURL(url(matchdayPath));
        page.navigate(url(path())); capture("pick-deadline-review-" + viewport);
        assertThat(page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Manage matchday picks and deadline").setExact(true)))
                .hasAttribute("href", matchdayPath + "/edit-pairings");
        page.navigate(url("/admin/matchday-picks?seasonId=" + fixture.season().getId()));
        assertThat(page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Back to matchday").setExact(true))).hasCount(0);
    }


    @ParameterizedTest
    @CsvSource({"1440,1000,desktop", "390,844,mobile"})
    void givenPreparedRoundWithoutJavascript_whenOpeningSettingAMatchExceptionAndClosing_thenUseTheCentralControls(
            int width, int height, String viewport) throws Exception {
        tx.executeWithoutResult(status -> races.findById(fixture.race().getId()).orElseThrow().getMatchday().setPicksOpen(false));
        page.setViewportSize(width, height);
        String dayPath = "/admin/matchdays/" + fixture.matchday().getId();
        page.navigate(url(dayPath)); assertThat(page.getByTestId("matchday-picks-state")).hasText("Closed");
        page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Manage picks and deadline").setExact(true)).click();
        var opening = page.getByRole(AriaRole.SWITCH, new Page.GetByRoleOptions().setName("Open picks for this matchday").setExact(true));
        assertThat(opening).not().isChecked(); opening.focus(); assertThat(opening).isFocused(); opening.press("Space");
        assertThat(opening).isChecked(); capture("matchday-opening-" + viewport);
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Save picks settings and event window")).click();
        assertThat(page.getByTestId("matchday-picks-state")).hasText("Open");
        page.navigate(url("/admin/matches/" + fixture.match().getId()));
        page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Match picks deadline").setExact(true)).click();
        page.getByLabel("Deadline exception, Europe/Berlin").fill("2036-01-01T20:00"); capture("match-deadline-exception-" + viewport);
        org.assertj.core.api.Assertions.assertThat((Boolean) page.evaluate("document.documentElement.scrollWidth <= window.innerWidth")).isTrue();
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Save match deadline")).click();
        page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Match picks deadline").setExact(true)).click();
        assertThat(page.getByLabel("Deadline exception, Europe/Berlin")).hasValue("2036-01-01T20:00");
        page.getByLabel("Deadline exception, Europe/Berlin").fill("");
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Save match deadline")).click();
        page.navigate(url(dayPath + "/edit-pairings"));
        assertThat(page.locator("#pickDeadline")).hasValue("2035-01-01T12:00");
        page.getByRole(AriaRole.SWITCH).uncheck();
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Save picks settings and event window")).click();
        assertThat(page.getByTestId("matchday-picks-state")).hasText("Closed");
        org.assertj.core.api.Assertions.assertThat(admin.detail(fixture.race().getId()).row().version()).isZero();
        org.assertj.core.api.Assertions.assertThat(admin.detail(fixture.race().getId()).row().matchdayOpen()).isFalse();
    }

    @BeforeEach
    void createSyntheticFixtureAndNativeBrowser() {
        // given
        context = browser.newContext(new Browser.NewContextOptions().setJavaScriptEnabled(false)); page = context.newPage();
        tx.executeWithoutResult(status -> {
            fixture = helper.createFullSeasonFixture("TPM" + UUID.randomUUID().toString().substring(0, 7));
            fixture.race().setDateTime(LocalDateTime.of(2035, 1, 1, 20, 0)); fixture.matchday().setPickDeadline(LocalDateTime.of(2035, 1, 1, 12, 0)); fixture.matchday().setPicksOpen(true);
            var car = cars.save(new Car("Test review", "Choice " + UUID.randomUUID())); carIds.add(car.getId());
            var track = tracks.save(new Track("Test review track " + UUID.randomUUID()));
            fixture.season().getCars().add(car); fixture.season().getTracks().add(track);
            payload = new PickPayload(track.getId(), car.getId(), null, 12, 2, 3, 4, PickInitialFuel.DEFAULT, 1,
                    TimeProgressionMode.EQUAL_CONDITIONS, null, "Test slot 1: S01; Test slot 2: R03", "Sunset", Set.of(PickTyre.RS, PickTyre.RM), Set.of(PickTyre.RM));
        });
    }

    @AfterEach
    void cleanUpSyntheticFixture() {
        teardownPage();
        tx.executeWithoutResult(status -> {
            helper.deleteSeasonCascade(seasons.findById(fixture.season().getId()).orElseThrow()); seasons.flush();
            cars.deleteAllById(carIds); tracks.deleteById(payload.trackId());
            teams.deleteAllById(List.of(fixture.homeTeam().getId(), fixture.awayTeam().getId()));
        });
        carIds.clear();
    }

    @ParameterizedTest
    @CsvSource({"1440,1000,desktop", "390,844,mobile"})
    void givenSubmittedPickWithDifferingSetupWithoutJavascript_whenReviewingByKeyboardThenEditingRace_thenKeepPickHistory(
            int width, int height, String viewport) throws Exception {
        // given
        preconfigureAndSubmit(); page.setViewportSize(width, height);
        page.navigate(url("/admin/races/" + fixture.race().getId()));
        page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Review team picks").setExact(true)).click();
        assertThat(page.getByTestId("pick-review-state")).hasText("SUBMITTED");
        assertThat(page.getByTestId("pick-review-target")).containsText(fixture.matchday().getLabel());
        page.getByLabel("Decision", new Page.GetByLabelOptions().setExact(true)).selectOption("APPROVE");
        page.getByLabel("Decision reason", new Page.GetByLabelOptions().setExact(true)).fill("Test approval checked with keyboard");
        // when
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Save review decision")).press("Enter");
        // then
        assertThat(page.getByRole(AriaRole.ALERT).first()).containsText("replace consent");
        assertThat(page.getByLabel("Decision reason", new Page.GetByLabelOptions().setExact(true))).hasValue("Test approval checked with keyboard");
        var replaceBox = page.getByLabel("Replace the differing internal settings when approving this revision").boundingBox();
        org.assertj.core.api.Assertions.assertThat(replaceBox.width).isLessThanOrEqualTo(24);
        org.assertj.core.api.Assertions.assertThat(replaceBox.height).isLessThanOrEqualTo(24);
        capture("pick-review-" + viewport);
        page.getByLabel("Replace the differing internal settings when approving this revision").check();
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Save review decision")).press("Enter");
        assertThat(page.getByTestId("pick-review-state")).hasText("APPROVED");
        page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Edit race settings")).click();
        page.getByLabel("Number of Laps", new Page.GetByLabelOptions().setExact(true)).fill("22");
        capture("pick-approved-race-edit-" + viewport);
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Save Race")).click();
        page.navigate(url(path()));
        assertThat(page.getByTestId("pick-review-state")).hasText("APPROVED");
        page.locator("#pick-revisions summary").first().click();
        assertThat(page.locator("#pick-revisions")).containsText("Test slot 1: S01; Test slot 2: R03");
        capture("pick-review-approved-" + viewport);
        var saved = admin.detail(fixture.race().getId());
        tx.executeWithoutResult(status -> org.assertj.core.api.Assertions.assertThat(races.findById(fixture.race().getId()).orElseThrow()
                .getSettings().getNumberOfLaps()).as("management edits the approved race directly").isEqualTo(22));
        org.assertj.core.api.Assertions.assertThat(saved.row().state()).isEqualTo(PickState.APPROVED);
        org.assertj.core.api.Assertions.assertThat(saved.row().approvedRevisionNumber()).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(saved.row().version()).isEqualTo(2);
        org.assertj.core.api.Assertions.assertThat(saved.revisions()).hasSize(1);
    }




    void preconfigureAndSubmit() {
        tx.executeWithoutResult(status -> {
            var race = races.findById(fixture.race().getId()).orElseThrow(); race.setCar(cars.findById(payload.carId()).orElseThrow());
            race.setTrack(tracks.findById(payload.trackId()).orElseThrow());
            if (payload.carPoolEntryId() != null) race.setCarPoolEntry(entries.findById(payload.carPoolEntryId()).orElseThrow());
            var setup = new RaceSettings(race); RaceSettingsValues.from(payload).applyTo(setup); setup.setNumberOfLaps(99); race.setSettings(setup);
        });
        submissions.submit(fixture.race().getId(), 0, UUID.randomUUID(), new AuthorizedPickActor(fixture.race().getId(), fixture.season().getId(), fixture.homeTeam().getId(), "12345"), payload);
    }
    void approve() { reviews.approve(fixture.race().getId(), 1, 1, new ManagementPickActor("test-e2e-manager"), "Test explicit replacement", true, admin.detail(fixture.race().getId()).fingerprint()); }
    String path() { return "/admin/matchday-picks/" + fixture.race().getId(); }
    void capture(String name) throws Exception {
        org.assertj.core.api.Assertions.assertThat(page.evaluate("document.documentElement.scrollWidth <= window.innerWidth")).isEqualTo(true);
        Files.createDirectories(Path.of(".screenshots"));
        page.screenshot(new Page.ScreenshotOptions().setPath(Path.of(".screenshots/" + name + ".png")).setFullPage(true));
        Files.writeString(Path.of(".screenshots/" + name + ".html"), page.content());
    }
    static void metadata(Car car, Drivetrain drivetrain) {
        car.setCategory(CarCategory.GR_3); car.setDrivetrain(drivetrain); car.setModelYear(9897); car.setModelYearOverride(true);
        car.setModelYearSource("Test fixture"); car.setModelYearEvidence("Test accepted local year");
    }
}
