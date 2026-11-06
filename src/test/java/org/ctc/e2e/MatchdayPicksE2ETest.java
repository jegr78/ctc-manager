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
    @Autowired org.ctc.admin.service.LobbySettingsGraphicService lobbyGraphics;
    @MockitoBean PickCollectionStatus collection;
    TestHelper.SeasonFixture fixture;
    PickPayload payload;
    final List<UUID> carIds = new ArrayList<>();

    @ParameterizedTest
    @CsvSource({"1440,1000,desktop,false", "390,844,mobile,false", "1440,1000,desktop-js,true", "390,844,mobile-js,true"})
    void givenExplicit2027OneMakeWithAndWithoutJavaScript_whenSavingNativeAndCorrectingApprovedTyres_thenRetainOneFamilyAndDryRequiredCompounds(
            int width, int height, String viewport, boolean javascript) throws Exception {
        // given
        if (javascript) { context.close(); context = browser.newContext(); page = context.newPage(); }
        when(collection.isConfidentialityReady()).thenReturn(true);
        tx.executeWithoutResult(status -> seasons.findById(fixture.season().getId()).orElseThrow().setLobbyRulesProfile(LobbyRulesProfile.CTC_2027));
        pools.changeMode(fixture.season().getId(), CarPoolMode.CATEGORY_POOL);
        var entry = pools.saveOneMakeEntry(fixture.season().getId(), null, "Test explicit tyre One-Make", payload.carId());
        pools.freeze(fixture.season().getId());
        payload = new PickPayload(payload.trackId(), payload.carId(), entry, payload.laps(), payload.tyreWear(), payload.fuelConsumption(),
                payload.refuelingSpeed(), payload.initialFuel(), payload.requiredPitStops(), payload.timeProgressionMode(), payload.timeProgressionMultiplier(),
                payload.weather(), payload.timeOfDay(), payload.availableTyres(), payload.mandatoryTyres(), LobbyRulesProfile.CTC_2027,
                RaceStartType.GRID_FALSE_START, RaceSlipstream.REAL);
        tx.executeWithoutResult(status -> {
            var race = races.findById(fixture.race().getId()).orElseThrow(); race.setPicksManaged(false);
            race.setCar(cars.findById(payload.carId()).orElseThrow()); race.setCarPoolEntry(entries.findById(entry).orElseThrow());
            race.setTrack(tracks.findById(payload.trackId()).orElseThrow());
            var settings = new RaceSettings(race); RaceSettingsValues.from(payload).applyTo(settings); race.setSettings(settings);
        });
        page.setViewportSize(width, height); page.navigate(url("/admin/races/" + fixture.race().getId() + "/edit"));
        assertThat(page.locator("#availableTyres option")).hasCount(6);
        assertThat(page.locator("#mandatoryTyres option[value=I], #mandatoryTyres option[value=W]")).hasCount(0);
        // when
        page.locator("#availableTyres").selectOption(new String[] {"SS", "SM", "SH"});
        if (!javascript) page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Update mandatory choices")).click();
        assertThat(page.locator("#mandatoryTyres option")).hasCount(javascript ? 4 : 5);
        if (!javascript) assertThat(page.locator("#mandatoryTyres option[selected]")).containsText("Unavailable saved or submitted selection");
        page.locator("#mandatoryTyres").selectOption("SM"); capture("tyres-native-" + viewport);
        page.locator(".entity-editor-actions button").click();
        // then
        assertThat(page).hasURL(url("/admin/races?matchdayId=" + fixture.matchday().getId()));
        page.navigate(url("/admin/races/" + fixture.race().getId() + "/edit"));
        assertThat(page.locator("#availableTyres")).hasValues(new String[] {"SS", "SM", "SH"});
        assertThat(page.locator("#mandatoryTyres")).hasValues(new String[] {"SM"});
        tx.executeWithoutResult(status -> races.findById(fixture.race().getId()).orElseThrow().setPicksManaged(true));
        preconfigureAndSubmit(); approve(); page.navigate(url(path() + "/correction"));
        assertThat(page.locator("#correct-mandatory-I, #correct-mandatory-W")).hasCount(0);
        for (var checkbox : page.locator("input[name=availableTyres]").all()) checkbox.uncheck();
        for (String tyre : List.of("SS", "SM", "SH")) page.locator("#correct-available-" + tyre).check();
        if (!javascript) page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Update mandatory choices")).click();
        assertThat(page.locator("#correct-mandatory-options .pick-check-option:not(.field-error) input[name=mandatoryTyres]")).hasCount(3);
        if (!javascript) assertThat(page.locator("#correct-retained-mandatory-RM")).isChecked();
        for (var checkbox : page.locator("input[name=mandatoryTyres]").all()) checkbox.uncheck();
        page.locator("input[name=mandatoryTyres][value=SM]").check(); page.locator("#correct-available-RS").check();
        page.getByLabel("Reason for internal correction").fill("Test single-family Sports correction");
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Save internal correction")).click();
        assertThat(page.getByRole(AriaRole.ALERT).first()).containsText("not saved");
        org.assertj.core.api.Assertions.assertThat(admin.detail(fixture.race().getId()).row().version()).isEqualTo(2);
        assertThat(page.locator("#correct-available-SS")).isChecked();
        page.locator("#correct-available-RS").uncheck();
        if (!javascript) page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Update mandatory choices")).click();
        page.locator("input[name=mandatoryTyres][value=SM]").check(); capture("tyres-correction-" + viewport);
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Save internal correction")).click();
        org.assertj.core.api.Assertions.assertThat(admin.detail(fixture.race().getId()).row().version()).isEqualTo(3);
        tx.executeWithoutResult(status -> {
            var settings = races.findById(fixture.race().getId()).orElseThrow().getSettings();
            org.assertj.core.api.Assertions.assertThat(settings.getAvailableTyres()).isEqualTo("SS, SM, SH");
            org.assertj.core.api.Assertions.assertThat(settings.getMandatoryTyres()).isEqualTo("SM");
        });
        var graphic = tx.execute(status -> org.junit.jupiter.api.Assertions.assertDoesNotThrow(() ->
                lobbyGraphics.renderPublication(races.findById(fixture.race().getId()).orElseThrow())));
        Files.write(Path.of(".screenshots/tyres-lobby-" + viewport + ".png"), graphic.png());
        var image = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(graphic.png()));
        org.assertj.core.api.Assertions.assertThat(image).isNotNull();
        org.assertj.core.api.Assertions.assertThat(image.getHeight()).isGreaterThan(1000);
    }

    @ParameterizedTest
    @CsvSource({"1440,1000,desktop", "390,844,mobile"})
    void givenMatchdayReviewEntryWithoutJavascript_whenFilteringAndReturning_thenKeepTheSelectedMatchday(
            int width, int height, String viewport) throws Exception {
        // given
        page.setViewportSize(width, height);
        String matchdayPath = "/admin/matchdays/" + fixture.matchday().getId();
        page.navigate(url(matchdayPath)); capture("pick-deadline-matchday-" + viewport);
        assertThat(page.locator("#matchday-picks")).containsText("Europe/Berlin");
        page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Edit pick deadline").setExact(true)).click();
        assertThat(page.locator("#pickDeadline")).hasValue("2035-01-01T12:00");
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Save deadline and event window")).click();
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
        assertThat(page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Edit pick deadline").setExact(true)))
                .hasAttribute("href", matchdayPath + "/edit-pairings");
        page.navigate(url("/admin/matchday-picks?seasonId=" + fixture.season().getId()));
        assertThat(page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Back to matchday").setExact(true))).hasCount(0);
    }

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

    @ParameterizedTest
    @CsvSource({"1440,1000,desktop", "390,844,mobile"})
    void givenApproved2027PickWithoutJavaScript_whenCorrectingStartAndSlipstream_thenRetainOriginalRevisionAndSaveExactChoices(int width, int height, String viewport) throws Exception {
        // given
        tx.executeWithoutResult(status -> seasons.findById(fixture.season().getId()).orElseThrow().setLobbyRulesProfile(LobbyRulesProfile.CTC_2027));
        payload = new PickPayload(payload.trackId(), payload.carId(), payload.carPoolEntryId(), payload.laps(), payload.tyreWear(),
                payload.fuelConsumption(), payload.refuelingSpeed(), payload.initialFuel(), payload.requiredPitStops(), payload.timeProgressionMode(),
                payload.timeProgressionMultiplier(), payload.weather(), payload.timeOfDay(), payload.availableTyres(), payload.mandatoryTyres(),
                LobbyRulesProfile.CTC_2027, RaceStartType.ROLLING, RaceSlipstream.WEAK);
        preconfigureAndSubmit(); approve(); page.setViewportSize(width, height); page.navigate(url(path() + "/correction"));
        assertThat(page.locator("input[name=lobbyRulesProfile]")).hasValue("CTC_2027");
        assertThat(page.getByLabel("Start type", new Page.GetByLabelOptions().setExact(true))).hasValue("ROLLING");
        assertThat(page.getByLabel("Race slipstream", new Page.GetByLabelOptions().setExact(true))).hasValue("WEAK");
        // when
        page.getByLabel("Start type", new Page.GetByLabelOptions().setExact(true)).selectOption("GRID_FALSE_START");
        page.getByLabel("Race slipstream", new Page.GetByLabelOptions().setExact(true)).selectOption("REAL");
        page.getByLabel("Reason for internal correction").fill("Test 2027 start and slipstream correction");
        capture("lobby-correction-" + viewport);
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Save internal correction")).click();
        // then
        assertThat(page.getByTestId("pick-review-state")).hasText("APPROVED");
        var detail = admin.detail(fixture.race().getId());
        org.assertj.core.api.Assertions.assertThat(detail.row().version()).isEqualTo(3);
        org.assertj.core.api.Assertions.assertThat(detail.revisions()).hasSize(1);
        tx.executeWithoutResult(status -> {
            var settings = races.findById(fixture.race().getId()).orElseThrow().getSettings();
            org.assertj.core.api.Assertions.assertThat(settings.getStartType()).isEqualTo(RaceStartType.GRID_FALSE_START);
            org.assertj.core.api.Assertions.assertThat(settings.getSlipstream()).isEqualTo(RaceSlipstream.REAL);
        });
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
