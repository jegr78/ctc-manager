package org.ctc.e2e;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.options.FormData;
import com.microsoft.playwright.options.RequestOptions;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.ctc.TestHelper;
import org.ctc.domain.model.*;
import org.ctc.domain.repository.*;
import org.ctc.domain.service.SeasonCarPoolService;
import org.ctc.domain.service.RaceService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;
import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

@Tag("e2e")
class CategoryRaceSelectionE2ETest extends PlaywrightConfig {
    @Autowired TestHelper helper;
    @Autowired SeasonRepository seasons;
    @Autowired TeamRepository teams;
    @Autowired CarRepository cars;
    @Autowired RaceRepository races;
    @Autowired SeasonCarPoolService pools;
    @Autowired TransactionTemplate tx;
    @Autowired RaceService raceService;
    TestHelper.SeasonFixture fixture;
    UUID combined;
    UUID alternative;
    UUID mr;
    UUID rr;
    UUID fr;
    UUID second;

    @BeforeEach
    void setUp() {
        setupPage();
        tx.executeWithoutResult(status -> {
            String suffix = UUID.randomUUID().toString().substring(0, 8);
            fixture = helper.createFullSeasonFixture("TCE" + suffix);
            fixture.homeTeam().setName("Test long home " + "W".repeat(220));
            mr = car(suffix, "MR");
            rr = car(suffix, "RR");
            fr = car(suffix, "FR");
            pools.changeMode(fixture.season().getId(), CarPoolMode.CATEGORY_POOL);
            combined = pools.saveEntry(fixture.season().getId(), null, "P".repeat(120), List.of(
                    new SeasonCarPoolService.ClauseInput(CarCategory.GR_3, Drivetrain.MR, 9895),
                    new SeasonCarPoolService.ClauseInput(CarCategory.GR_3, Drivetrain.RR, 9895)));
            alternative = pools.saveEntry(fixture.season().getId(), null, "Test <alternative>", List.of(
                    new SeasonCarPoolService.ClauseInput(CarCategory.GR_3, Drivetrain.FR, 9895)));
            pools.freeze(fixture.season().getId());
            var race = new Race();
            race.setMatchday(fixture.matchday());
            race.setMatch(fixture.match());
            second = races.saveAndFlush(race).getId();
        });
    }

    @AfterEach
    void cleanUp() {
        teardownPage();
        if (fixture == null) return;
        tx.executeWithoutResult(status -> {
            helper.deleteSeasonCascade(seasons.findById(fixture.season().getId()).orElseThrow());
            seasons.flush();
            cars.deleteAllById(List.of(mr, rr, fr));
            teams.deleteAll(List.of(fixture.homeTeam(), fixture.awayTeam()));
        });
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void givenFrozenCombinedEntry_whenSelectingMrThenEditingAnotherRace_thenWholeEntryIsConsumed(boolean javaScript) {
        // given
        try (var editorContext = browser.newContext(new Browser.NewContextOptions().setJavaScriptEnabled(javaScript))) {
            var editor = editorContext.newPage();
            editor.navigate(url("/admin/races/" + fixture.race().getId() + "/edit"));
            // when
            editor.locator("#carPoolEntryId").selectOption(combined.toString());
            editor.locator("#numberOfLaps").fill("19");
            if (!javaScript) editor.getByText("Refresh choices", new com.microsoft.playwright.Page.GetByTextOptions().setExact(true)).click();
            // then
            assertThat(editor.locator("#carId option[value='" + mr + "']")).hasCount(1);
            assertThat(editor.locator("#carId option[value='" + rr + "']")).hasCount(1);
            assertThat(editor.locator("#numberOfLaps")).hasValue("19");
            // when
            editor.locator("#carId").selectOption(mr.toString());
            editor.locator(".entity-editor-actions button").click();
            // then
            assertThat(editor).hasURL(url("/admin/races?matchdayId=" + fixture.matchday().getId()));
            tx.executeWithoutResult(status -> {
                var saved = races.findById(fixture.race().getId()).orElseThrow();
                org.assertj.core.api.Assertions.assertThat(saved.getCar().getId()).isEqualTo(mr);
                org.assertj.core.api.Assertions.assertThat(saved.getCarPoolEntry().getId()).isEqualTo(combined);
            });
            // when
            editor.navigate(url("/admin/races/" + second + "/edit"));
            // then
            assertThat(editor.locator("#carPoolEntryId option[value='" + combined + "']")).isDisabled();
            assertThat(editor.locator("#carPoolEntryId option[value='" + combined + "']")).containsText("already used");
            assertThat(editor.locator("#carPoolEntryId option[value='" + alternative + "']")).isEnabled();
        }
    }

    @Test
    void givenDelayedHomeResponseBodies_whenLatestHomeFinishesFirst_thenIgnoreEarlierResponseEvenAfterJsonAwait() {
        // given
        installControlledAvailability();
        page.navigate(url("/admin/races/" + second + "/edit"));
        // when
        page.locator("#homeTeamId").selectOption(fixture.awayTeam().getId().toString());
        page.locator("#homeTeamId").selectOption(fixture.homeTeam().getId().toString());
        org.assertj.core.api.Assertions.assertThat(page.evaluate("window.ctcSelectionRequests.length")).isEqualTo(2);
        resolve(1, true);
        // then
        assertThat(page.locator("#carPoolEntryId option[value='" + combined + "']")).isDisabled();
        // when
        resolve(0, false);
        // then
        assertThat(page.locator("#homeTeamId")).hasValue(fixture.homeTeam().getId().toString());
        assertThat(page.locator("#carPoolEntryId option[value='" + combined + "']")).isDisabled();
        assertThat(page.locator("[data-used-selection-status]")).hasText("Availability updated for the home team.");
    }

    @Test
    void givenRapidEntryChangesWithDelayedBodies_whenEarlierEntryFinishesLast_thenKeepLatestEntryAndMemberOptions() {
        // given
        installControlledAvailability();
        page.navigate(url("/admin/races/" + second + "/edit"));
        // when
        page.locator("#carPoolEntryId").selectOption(combined.toString());
        page.locator("#carPoolEntryId").selectOption(alternative.toString());
        org.assertj.core.api.Assertions.assertThat(page.evaluate("window.ctcSelectionRequests.length")).isEqualTo(2);
        resolve(1, false);
        // then
        assertThat(page.locator("#carPoolEntryId")).hasValue(alternative.toString());
        assertThat(page.locator("#carId option[value='" + fr + "']")).hasCount(1);
        // when
        resolve(0, false);
        // then
        assertThat(page.locator("#carPoolEntryId")).hasValue(alternative.toString());
        assertThat(page.locator("#carId option[value='" + fr + "']")).hasCount(1);
        assertThat(page.locator("#carId option[value='" + mr + "']")).hasCount(0);
        assertThat(page.locator("alternative")).hasCount(0);
    }

    @Test
    void givenMaximumPoolLabelAndLongTeamName_whenUsingMobileEditor_thenControlsStayInsideViewport() {
        // given
        page.setViewportSize(375, 812);
        page.navigate(url("/admin/races/" + second + "/edit"));
        // when
        page.locator("#carPoolEntryId").selectOption(combined.toString());
        // then
        org.assertj.core.api.Assertions.assertThat((Boolean) page.evaluate(
                "document.documentElement.scrollWidth <= innerWidth && document.body.scrollWidth <= innerWidth")).isTrue();
        for (String selector : List.of("#carPoolEntryId", "#homeTeamId", "#carId")) {
            var bounds = page.locator(selector).boundingBox();
            org.assertj.core.api.Assertions.assertThat(bounds.x + bounds.width).isLessThanOrEqualTo(375);
        }
        page.locator("a[href='#race-catalog']").click();
        org.assertj.core.api.Assertions.assertThat(page.locator("#race-catalog").boundingBox().y).isGreaterThanOrEqualTo(72);
    }

    @Test
    void givenDisabledSubmittedChoicesWithoutJavascript_whenCorrectingSettingsAndResubmitting_thenRejectReuseWithoutClearingOldSelection() {
        // given
        nativeSelect(fixture.race().getId(), fixture.homeTeam().getId(), fixture.awayTeam().getId());
        UUID independent = tx.execute(status -> {
            var season = seasons.findById(fixture.season().getId()).orElseThrow();
            var md = helper.createMatchdayInRegularPhase(season, "Test native independent return", 2);
            var match = helper.createMatch(md, fixture.awayTeam(), fixture.homeTeam());
            return helper.createRace(md, match).getId();
        });
        nativeSelect(independent, fixture.awayTeam().getId(), fixture.homeTeam().getId());
        try (var nativeContext = browser.newContext(new Browser.NewContextOptions().setJavaScriptEnabled(false))) {
            var editor = nativeContext.newPage();
            String editUrl = url("/admin/races/" + fixture.race().getId() + "/edit");
            var response = nativeContext.request().post(url("/admin/races/save"), RequestOptions.create().setMaxRedirects(0)
                    .setForm(FormData.create().set("id", fixture.race().getId().toString())
                            .set("matchdayId", fixture.matchday().getId().toString())
                            .set("homeTeamId", fixture.awayTeam().getId().toString())
                            .set("awayTeamId", fixture.homeTeam().getId().toString())
                            .set("carPoolEntryId", combined.toString()).set("carId", rr.toString()).set("numberOfLaps", "bad-laps")));
            org.assertj.core.api.Assertions.assertThat(response.status()).isEqualTo(302);
            editor.navigate(editUrl);
            assertThat(editor.locator("#numberOfLaps")).hasValue("bad-laps");
            assertThat(editor.locator("#carPoolEntryId option[value='" + combined + "']")).isDisabled();
            assertThat(editor.locator("#carId option[value='" + rr + "']")).isDisabled();
            // when
            editor.locator("#numberOfLaps").fill("99");
            editor.locator(".entity-editor-actions button").click();
            // then
            assertThat(editor).hasURL(editUrl);
            assertThat(editor.locator(".alert-error")).containsText("already");
            tx.executeWithoutResult(status -> {
                var saved = races.findById(fixture.race().getId()).orElseThrow();
                org.assertj.core.api.Assertions.assertThat(saved.getCarPoolEntry().getId()).isEqualTo(combined);
                org.assertj.core.api.Assertions.assertThat(saved.getCar().getId()).isEqualTo(mr);
                org.assertj.core.api.Assertions.assertThat(saved.getHomeTeam().getId()).isEqualTo(fixture.homeTeam().getId());
                org.assertj.core.api.Assertions.assertThat(saved.getSettings().getNumberOfLaps()).isEqualTo(5);
            });
        }
    }

    @Test
    void givenAvailabilityHttpFailure_whenChangingEntry_thenKeepInputAndRecoverThroughNativeRefresh() {
        // given
        page.addInitScript("window.fetch = () => Promise.resolve({ok: false, json: () => Promise.resolve({})});");
        page.navigate(url("/admin/races/" + second + "/edit"));
        // when
        page.locator("#numberOfLaps").fill("33");
        page.locator("#carPoolEntryId").selectOption(combined.toString());
        // then
        assertThat(page.locator("[data-used-selection-status]")).hasText("Could not refresh choices. Use Refresh choices to try again.");
        assertThat(page.locator("#carPoolEntryId")).hasValue(combined.toString());
        // when
        page.getByText("Refresh choices", new com.microsoft.playwright.Page.GetByTextOptions().setExact(true)).click();
        // then
        assertThat(page.locator("#carId option[value='" + mr + "']")).hasCount(1);
        assertThat(page.locator("#carId option[value='" + rr + "']")).hasCount(1);
        assertThat(page.locator("#numberOfLaps")).hasValue("33");
    }

    @Test
    void givenRejectedDateTime_whenRefreshingAndCorrectingInLocalTimezone_thenKeepRawValueWithoutScriptErrorsAndSaveLondonTime() {
        // given
        try (var localContext = browser.newContext(new Browser.NewContextOptions().setTimezoneId("Europe/Berlin"))) {
            var editor = localContext.newPage();
            var errors = new ArrayList<String>();
            editor.onPageError(errors::add);
            String editUrl = url("/admin/races/" + fixture.race().getId() + "/edit");
            var response = localContext.request().post(url("/admin/races/save"), RequestOptions.create().setMaxRedirects(0)
                    .setForm(FormData.create().set("id", fixture.race().getId().toString())
                            .set("matchdayId", fixture.matchday().getId().toString())
                            .set("homeTeamId", fixture.homeTeam().getId().toString())
                            .set("awayTeamId", fixture.awayTeam().getId().toString())
                            .set("dateTime", "not-a-date").set("numberOfLaps", "12")));
            org.assertj.core.api.Assertions.assertThat(response.status()).isEqualTo(302);
            editor.navigate(editUrl);
            assertThat(editor.locator("#dateTime")).hasValue("not-a-date");
            // when
            editor.getByText("Refresh choices", new com.microsoft.playwright.Page.GetByTextOptions().setExact(true)).click();
            // then
            assertThat(editor.locator("#dateTime")).hasValue("not-a-date");
            assertThat(editor.locator("#dateTime-error")).hasText("Enter a valid date and time.");
            org.assertj.core.api.Assertions.assertThat(errors).isEmpty();
            // when
            editor.locator("#dateTime").fill("2026-01-01T21:30");
            editor.locator(".entity-editor-actions button").click();
            // then
            assertThat(editor).hasURL(url("/admin/races?matchdayId=" + fixture.matchday().getId()));
            tx.executeWithoutResult(status -> org.assertj.core.api.Assertions.assertThat(
                    races.findById(fixture.race().getId()).orElseThrow().getDateTime())
                    .isEqualTo(java.time.LocalDateTime.of(2026, 1, 1, 20, 30)));
            org.assertj.core.api.Assertions.assertThat(errors).isEmpty();
        }
    }

    @Test
    void givenOwnCombinedSelection_whenSwitchingToAnotherEntry_thenSaveItsMemberAndReleaseTheOldEntry() {
        // given
        nativeSelect(fixture.race().getId(), fixture.homeTeam().getId(), fixture.awayTeam().getId());
        page.navigate(url("/admin/races/" + fixture.race().getId() + "/edit"));
        // when
        page.locator("#carPoolEntryId").selectOption(alternative.toString());
        // then
        assertThat(page.locator("#carId option[value='" + fr + "']")).hasCount(1);
        // when
        page.locator("#carId").selectOption(fr.toString());
        page.locator(".entity-editor-actions button").click();
        // then
        assertThat(page).hasURL(url("/admin/races?matchdayId=" + fixture.matchday().getId()));
        tx.executeWithoutResult(status -> {
            var saved = races.findById(fixture.race().getId()).orElseThrow();
            org.assertj.core.api.Assertions.assertThat(saved.getCarPoolEntry().getId()).isEqualTo(alternative);
            org.assertj.core.api.Assertions.assertThat(saved.getCar().getId()).isEqualTo(fr);
        });
        // when
        page.navigate(url("/admin/races/" + second + "/edit"));
        // then
        assertThat(page.locator("#carPoolEntryId option[value='" + combined + "']")).isEnabled();
        assertThat(page.locator("#carPoolEntryId option[value='" + alternative + "']")).isDisabled();
    }

    private void nativeSelect(UUID raceId, UUID home, UUID away) {
        UUID matchday = tx.execute(status -> races.findById(raceId).orElseThrow().getMatchday().getId());
        org.assertj.core.api.Assertions.assertThat(raceService.saveRace(raceId, matchday, home, away, null, mr, null,
                5, null, null, null, null, null, TimeProgressionMode.MULTIPLIER, 0, null, null, null, null, combined).success()).isTrue();
    }

    private void installControlledAvailability() {
        page.addInitScript("""
                window.ctcSelectionRequests = [];
                const originalFetch = window.fetch.bind(window);
                window.fetch = (input, options) => {
                    if (!String(input).includes('/admin/races/used-selections?')) return originalFetch(input, options);
                    const request = {url: String(input)};
                    window.ctcSelectionRequests.push(request);
                    return Promise.resolve({ok: true, json: () => new Promise(resolve => request.resolve = resolve)});
                };
                """);
    }

    private void resolve(int index, boolean consumed) {
        var payload = Map.of("carPoolMode", "CATEGORY_POOL", "carPoolState", "FROZEN",
                "usedCarIds", List.of(), "usedTrackIds", List.of(), "consumedEntryIds", consumed ? List.of(combined.toString()) : List.of(),
                "entries", List.of(
                        Map.of("id", combined.toString(), "label", "Test combined MR/RR", "consumed", consumed,
                                "reason", consumed ? "Already used by this home team this season." : "",
                                "cars", List.of(Map.of("id", mr.toString(), "label", "Test MR"), Map.of("id", rr.toString(), "label", "Test RR"))),
                        Map.of("id", alternative.toString(), "label", "Test <alternative>", "consumed", false, "reason", "",
                                "cars", List.of(Map.of("id", fr.toString(), "label", "Test FR")))),
                "fixedCars", List.of(), "tracks", List.of());
        page.evaluate("args => window.ctcSelectionRequests[args.index].resolve(args.payload)", Map.of("index", index, "payload", payload));
        // A protocol round trip drains the resolved promise continuation without a timer or retry.
        page.evaluate("Promise.resolve()");
    }

    private UUID car(String suffix, String name) {
        var car = new Car("Test browser " + suffix, name);
        car.setCategory(CarCategory.GR_3);
        car.setDrivetrain(Drivetrain.valueOf(name));
        car.setModelYear(9895);
        car.setModelYearOverride(true);
        car.setModelYearSource("Test fixture");
        car.setModelYearEvidence("Test accepted local year");
        return cars.saveAndFlush(car).getId();
    }
}
