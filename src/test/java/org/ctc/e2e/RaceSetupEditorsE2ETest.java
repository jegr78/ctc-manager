package org.ctc.e2e;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.microsoft.playwright.Browser;
import java.util.UUID;
import org.ctc.TestHelper;
import org.ctc.domain.model.Car;
import org.ctc.domain.model.Track;
import org.ctc.domain.repository.CarRepository;
import org.ctc.domain.repository.MatchdayRepository;
import org.ctc.domain.repository.RaceRepository;
import org.ctc.domain.repository.SeasonRepository;
import org.ctc.domain.repository.TrackRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

@Tag("e2e")
class RaceSetupEditorsE2ETest extends PlaywrightConfig {
    @Autowired TestHelper helper;
    @Autowired SeasonRepository seasons;
    @Autowired CarRepository cars;
    @Autowired TrackRepository tracks;
    @Autowired MatchdayRepository matchdays;
    @Autowired RaceRepository races;
    @Autowired TransactionTemplate transaction;

    @BeforeEach
    void setUp() { setupPage(); }

    @AfterEach
    void tearDown() { teardownPage(); }

    @Test
    void givenSeasonMatchday_whenInvalidLabelIsCorrected_thenOrderAndSeasonRemainAndSaveSucceeds() {
        // given
        var fixture = helper.createFullSeasonFixture("Test Setup " + UUID.randomUUID().toString().substring(0, 8));
        page.navigate(url("/admin/matchdays/" + fixture.matchday().getId() + "/edit"));

        // when
        page.locator(".entity-editor-form").evaluate("form => form.noValidate = true");
        page.locator("#label").fill("");
        page.locator("#sortIndex").fill("7");
        page.locator(".entity-editor-actions button").click();

        // then
        assertThat(page.locator("#label-error")).isVisible();
        assertThat(page.locator("#label")).hasAttribute("aria-invalid", "true");
        assertThat(page.locator("#sortIndex")).hasValue("7");
        assertThat(page.locator(".back-link")).hasAttribute("href", "/admin/matchdays?seasonId=" + fixture.season().getId());

        // when
        page.locator("#label").fill(fixture.matchday().getLabel());
        page.locator(".entity-editor-actions button").click();

        // then
        assertThat(page).hasURL(url("/admin/matchdays?seasonId=" + fixture.season().getId()));
        assertEquals(7, matchdays.findById(fixture.matchday().getId()).orElseThrow().getSortIndex());
    }

    @Test
    void givenJavaScriptDisabled_whenChooseCarTrackAndSaveSettings_thenNativeSelectionsAreRetained() {
        // given
        var suffix = UUID.randomUUID().toString().substring(0, 8);
        var fixture = helper.createFullSeasonFixture("Test Native Setup " + suffix);
        var car = cars.save(new Car("Test Setup", "Car " + suffix));
        var track = tracks.save(new Track("Test Setup Track " + suffix));
        transaction.executeWithoutResult(tx -> {
            var season = seasons.findById(fixture.season().getId()).orElseThrow();
            season.getCars().add(car);
            season.getTracks().add(track);
        });
        try (var nativeContext = browser.newContext(new Browser.NewContextOptions().setJavaScriptEnabled(false))) {
            var nativePage = nativeContext.newPage();
            nativePage.navigate(url("/admin/races/" + fixture.race().getId() + "/edit"));

            // when
            nativePage.locator("#carId").selectOption(car.getId().toString());
            nativePage.locator("#trackId").selectOption(track.getId().toString());
            nativePage.locator("#numberOfLaps").fill("12");
            nativePage.locator("#weather").fill("Clear");
            nativePage.locator(".entity-editor-actions button").click();

            // then
            assertThat(nativePage).hasURL(url("/admin/races?matchdayId=" + fixture.matchday().getId()));
            nativePage.navigate(url("/admin/races/" + fixture.race().getId() + "/edit"));
            assertThat(nativePage.locator("#carId")).hasValue(car.getId().toString());
            assertThat(nativePage.locator("#trackId")).hasValue(track.getId().toString());
            assertThat(nativePage.locator("#numberOfLaps")).hasValue("12");
            assertThat(nativePage.locator("#weather")).hasValue("Clear");
        }
    }
    @Test
    void givenPreviouslyUsedCatalogEntries_whenHomeTeamChanges_thenUnavailableSelectionsClearAndBecomeAvailableForAnotherTeam() {
        // given
        var suffix = UUID.randomUUID().toString().substring(0, 8);
        var fixture = helper.createFullSeasonFixture("Test Used Setup " + suffix);
        var car = cars.save(new Car("Test Used Setup", "Car " + suffix));
        var track = tracks.save(new Track("Test Used Setup Track " + suffix));
        transaction.executeWithoutResult(tx -> {
            var season = seasons.findById(fixture.season().getId()).orElseThrow();
            season.getCars().add(car);
            season.getTracks().add(track);
            var race = races.findById(fixture.race().getId()).orElseThrow();
            race.setCar(car);
            race.setTrack(track);
        });
        page.navigate(url("/admin/races/new?matchdayId=" + fixture.matchday().getId()));
        page.locator("#carId").selectOption(car.getId().toString());
        page.locator("#trackId").selectOption(track.getId().toString());

        // when
        page.locator("#homeTeamId").selectOption(fixture.homeTeam().getId().toString());

        // then
        assertThat(page.locator("#carId option[value='" + car.getId() + "']")).isDisabled();
        assertThat(page.locator("#trackId option[value='" + track.getId() + "']")).isDisabled();
        assertThat(page.locator("#carId")).hasValue("");
        assertThat(page.locator("#trackId")).hasValue("");
        assertThat(page.locator("[data-used-selection-status]")).hasText("Availability updated for the home team.");

        // when
        page.locator("#homeTeamId").selectOption(fixture.awayTeam().getId().toString());

        // then
        assertThat(page.locator("#carId option[value='" + car.getId() + "']")).isEnabled();
        assertThat(page.locator("#trackId option[value='" + track.getId() + "']")).isEnabled();
        page.locator("#carId").selectOption(car.getId().toString());
        assertThat(page.locator("#carId")).hasValue(car.getId().toString());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void givenSavedCarAndTrackRemovedFromPool_whenEditingSettings_thenSelectionsRemainUntilExplicitlyReplaced(boolean javaScriptEnabled) {
        // given
        var suffix = UUID.randomUUID().toString().substring(0, 8);
        var fixture = helper.createFullSeasonFixture("Test Removed Pool " + suffix);
        var savedCar = cars.save(new Car("Test Saved", "Car " + suffix));
        var savedTrack = tracks.save(new Track("Test Saved Track " + suffix));
        var replacementCar = cars.save(new Car("Test Replacement", "Car " + suffix));
        var replacementTrack = tracks.save(new Track("Test Replacement Track " + suffix));
        transaction.executeWithoutResult(tx -> {
            var season = seasons.findById(fixture.season().getId()).orElseThrow();
            season.getCars().add(replacementCar);
            season.getTracks().add(replacementTrack);
            var race = races.findById(fixture.race().getId()).orElseThrow();
            race.setCar(savedCar);
            race.setTrack(savedTrack);
        });
        try (var context = browser.newContext(new Browser.NewContextOptions().setJavaScriptEnabled(javaScriptEnabled))) {
            var editor = context.newPage();
            var editUrl = url("/admin/races/" + fixture.race().getId() + "/edit");

            // when
            editor.navigate(editUrl);
            editor.locator("#numberOfLaps").fill("12");

            // then
            assertThat(editor.locator("#carId")).hasValue(savedCar.getId().toString());
            assertThat(editor.locator("#trackId")).hasValue(savedTrack.getId().toString());
            assertThat(editor.locator("#car-pool-hint")).containsText("no longer in this season's pool");
            assertThat(editor.locator("#track-pool-hint")).containsText("no longer in this season's pool");

            // when
            editor.locator(".entity-editor-actions button").click();

            // then
            assertThat(editor).hasURL(editUrl);
            assertThat(editor.locator(".alert-error")).containsText("Car is not in this season's pool");
            transaction.executeWithoutResult(tx -> {
                var race = races.findById(fixture.race().getId()).orElseThrow();
                assertEquals(savedCar.getId(), race.getCar().getId());
                assertEquals(savedTrack.getId(), race.getTrack().getId());
            });

            // when
            editor.locator("#carId").selectOption(replacementCar.getId().toString());
            editor.locator(".entity-editor-actions button").click();

            // then
            assertThat(editor).hasURL(editUrl);
            assertThat(editor.locator(".alert-error")).containsText("Track is not in this season's pool");
            transaction.executeWithoutResult(tx -> {
                var race = races.findById(fixture.race().getId()).orElseThrow();
                assertEquals(savedCar.getId(), race.getCar().getId());
                assertEquals(savedTrack.getId(), race.getTrack().getId());
            });

            // when
            editor.locator("#carId").selectOption(replacementCar.getId().toString());
            editor.locator("#trackId").selectOption(replacementTrack.getId().toString());
            editor.locator("#numberOfLaps").fill("12");
            editor.locator(".entity-editor-actions button").click();

            // then
            assertThat(editor).hasURL(url("/admin/races?matchdayId=" + fixture.matchday().getId()));
            editor.navigate(editUrl);
            assertThat(editor.locator("#carId")).hasValue(replacementCar.getId().toString());
            assertThat(editor.locator("#trackId")).hasValue(replacementTrack.getId().toString());
            assertThat(editor.locator("#numberOfLaps")).hasValue("12");
        }
    }
}
