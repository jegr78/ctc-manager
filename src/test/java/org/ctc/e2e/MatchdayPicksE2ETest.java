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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.mockito.Mockito.when;

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
    @MockitoBean PickCollectionStatus collection;
    TestHelper.SeasonFixture fixture;
    PickPayload payload;
    final List<UUID> carIds = new ArrayList<>();

    @BeforeEach
    void createSyntheticFixtureAndNativeBrowser() {
        // given
        context = browser.newContext(new Browser.NewContextOptions().setJavaScriptEnabled(false)); page = context.newPage();
        when(collection.isAvailable()).thenReturn(true);
        tx.executeWithoutResult(status -> {
            fixture = helper.createFullSeasonFixture("TPM" + UUID.randomUUID().toString().substring(0, 7));
            fixture.season().setPicksCollectionEnabled(true); fixture.race().setPicksManaged(true);
            fixture.race().setDateTime(LocalDateTime.of(2035, 1, 1, 20, 0)); fixture.matchday().setPickDeadline(LocalDateTime.of(2035, 1, 1, 12, 0));
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
    void givenSubmittedPickWithDifferingSetupWithoutJavascript_whenReviewingByKeyboardThenCorrecting_thenKeepHistoryAndTeamLock(
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
        page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Correct approved settings")).click();
        page.getByLabel("Laps", new Page.GetByLabelOptions().setExact(true)).fill("22");
        page.getByLabel("Time progression mode").selectOption("MULTIPLIER"); page.getByLabel("Time progression multiplier").fill("0");
        page.getByLabel("Weather", new Page.GetByLabelOptions().setExact(true)).fill("Test corrected slots: " + "S01 ".repeat(40));
        page.getByLabel("Reason for internal correction").fill("Test reason with <escaped> private text " + "long value ".repeat(30));
        capture("pick-correction-" + viewport);
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Save internal correction")).click();
        assertThat(page.getByTestId("pick-review-state")).hasText("APPROVED");
        page.locator("#pick-revisions summary").first().click(); page.locator("#pick-decisions summary").first().click();
        assertThat(page.locator("#pick-revisions")).containsText("Test slot 1: S01; Test slot 2: R03");
        assertThat(page.locator("#pick-decisions")).containsText("0x");
        capture("pick-review-approved-" + viewport);
        var saved = admin.detail(fixture.race().getId());
        org.assertj.core.api.Assertions.assertThat(saved.row().state()).isEqualTo(PickState.APPROVED);
        org.assertj.core.api.Assertions.assertThat(saved.row().approvedRevisionNumber()).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(saved.row().version()).isEqualTo(3);
        org.assertj.core.api.Assertions.assertThat(saved.revisions()).hasSize(1);
    }

    @Test
    void givenMalformedNumericCorrection_whenServerRejects_thenKeepRawInputAndVisibleFieldError() throws Exception {
        // given
        preconfigureAndSubmit(); approve(); page.navigate(url(path() + "/correction"));
        page.getByLabel("Laps", new Page.GetByLabelOptions().setExact(true)).evaluate("input => input.type = 'text'");
        page.getByLabel("Laps", new Page.GetByLabelOptions().setExact(true)).fill("not-a-number");
        page.getByLabel("Reason for internal correction").fill("Test rejected numeric correction");
        page.locator("form.entity-editor-form").evaluate("form => form.noValidate = true");
        // when
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Save internal correction")).click();
        // then
        assertThat(page.getByLabel("Laps", new Page.GetByLabelOptions().setExact(true))).hasValue("not-a-number");
        assertThat(page.getByRole(AriaRole.ALERT).first()).containsText("not saved");
        assertThat(page.getByLabel("Reason for internal correction")).hasValue("Test rejected numeric correction");
        capture("pick-correction-invalid-desktop");
        org.assertj.core.api.Assertions.assertThat(admin.detail(fixture.race().getId()).row().version()).isEqualTo(2);
    }

    @Test
    void givenFrozenCategoryPoolWithoutJavascript_whenRefreshingEntryAndSavingCorrection_thenUseExactNewMemberAndRetainOriginalRevision() throws Exception {
        // given
        UUID[] choices = tx.execute(status -> {
            var mr = cars.findById(payload.carId()).orElseThrow(); metadata(mr, Drivetrain.MR);
            var rr = new Car("Test review", "Alternate RR " + UUID.randomUUID()); metadata(rr, Drivetrain.RR); rr = cars.saveAndFlush(rr); carIds.add(rr.getId());
            pools.changeMode(fixture.season().getId(), CarPoolMode.CATEGORY_POOL);
            var first = pools.saveEntry(fixture.season().getId(), null, "Test MR entry", List.of(new SeasonCarPoolService.ClauseInput(CarCategory.GR_3, Drivetrain.MR, 9897)));
            var second = pools.saveEntry(fixture.season().getId(), null, "Test RR entry", List.of(new SeasonCarPoolService.ClauseInput(CarCategory.GR_3, Drivetrain.RR, 9897)));
            pools.freeze(fixture.season().getId()); return new UUID[] {first, second, rr.getId()};
        });
        payload = new PickPayload(payload.trackId(), payload.carId(), choices[0], payload.laps(), payload.tyreWear(), payload.fuelConsumption(), payload.refuelingSpeed(),
                payload.initialFuel(), payload.requiredPitStops(), payload.timeProgressionMode(), payload.timeProgressionMultiplier(), payload.weather(), payload.timeOfDay(), payload.availableTyres(), payload.mandatoryTyres());
        preconfigureAndSubmit(); approve(); page.setViewportSize(390, 844); page.navigate(url(path() + "/correction"));
        page.getByLabel("Category pool entry").selectOption(choices[1].toString());
        page.getByLabel("Reason for internal correction").fill("Test category correction retained across refresh");
        // when
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Refresh choices")).click();
        // then
        assertThat(page.getByLabel("Reason for internal correction")).hasValue("Test category correction retained across refresh");
        page.getByLabel("Car", new Page.GetByLabelOptions().setExact(true)).selectOption(choices[2].toString());
        capture("pick-correction-category-mobile");
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Save internal correction")).click();
        org.assertj.core.api.Assertions.assertThat(admin.detail(fixture.race().getId()).row().version()).isEqualTo(3);
        tx.executeWithoutResult(status -> {
            var saved = races.findById(fixture.race().getId()).orElseThrow();
            org.assertj.core.api.Assertions.assertThat(saved.getCarPoolEntry().getId()).isEqualTo(choices[1]);
            org.assertj.core.api.Assertions.assertThat(saved.getCar().getId()).isEqualTo(choices[2]);
        });
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
