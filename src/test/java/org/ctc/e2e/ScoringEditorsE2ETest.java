package org.ctc.e2e;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.microsoft.playwright.Browser;
import java.util.UUID;
import org.ctc.domain.model.MatchScoring;
import org.ctc.domain.model.RaceScoring;
import org.ctc.domain.repository.MatchScoringRepository;
import org.ctc.domain.repository.RaceScoringRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@Tag("e2e")
class ScoringEditorsE2ETest extends PlaywrightConfig {
    @Autowired RaceScoringRepository raceRepository;
    @Autowired MatchScoringRepository matchRepository;
    private RaceScoring race;
    private MatchScoring match;

    @BeforeEach
    void setUp() { setupPage(); }

    @AfterEach
    void tearDown() {
        teardownPage();
        if (race != null) raceRepository.deleteById(race.getId());
        if (match != null) matchRepository.deleteById(match.getId());
    }

    @Test
    void givenRaceScoring_whenAddRemoveAndSaveInvalidName_thenFocusAndPointValuesRemainConsistent() {
        race = raceRepository.save(new RaceScoring("Test Editor Race " + UUID.randomUUID(), "20,17,14", "3,2", 1));
        page.navigate(url("/admin/race-scorings/" + race.getId() + "/edit"));
        var editor = page.locator("[data-points-editor='racePoints']");
        editor.locator("[data-points-add]").click();
        assertThat(page.locator("#racePoints-position-4")).isFocused();
        assertThat(page.locator("#racePoints-position-4")).hasValue("14");
        assertThat(page.locator("label[for='racePoints-position-4']")).hasText("Position 4");
        editor.locator("[data-points-remove]").click();
        assertThat(page.locator("#racePoints-position-3")).isFocused();
        page.locator("#racePoints-position-1").fill("25");
        page.locator("#qualiPoints-position-2").fill("1");
        page.locator("#scoringForm").evaluate("form => form.noValidate = true");
        page.locator("#name").fill("");
        page.locator(".entity-editor-actions button").click();
        assertThat(page.locator("#name-error")).isVisible();
        assertThat(page.locator("#racePoints-position-1")).hasValue("25");
        assertThat(page.locator("#qualiPoints-position-2")).hasValue("1");
        page.locator("#name").fill(race.getName());
        page.locator(".entity-editor-actions button").click();
        assertThat(page).hasURL(url("/admin/race-scorings"));
        var saved = raceRepository.findById(race.getId()).orElseThrow();
        assertEquals("25,17,14", saved.getRacePoints());
        assertEquals("3,1", saved.getQualiPoints());
        page.locator("[data-list-search]").fill(race.getName());
        assertThat(page.locator("[data-list-entry]:visible")).hasCount(1);
    }

    @Test
    void givenJavaScriptDisabled_whenEditRacePoints_thenNativeFormSavesPointSequence() {
        race = raceRepository.save(new RaceScoring("Test Native Race " + UUID.randomUUID(), "20,17", null, 0));
        try (var nativeContext = browser.newContext(new Browser.NewContextOptions().setJavaScriptEnabled(false))) {
            var nativePage = nativeContext.newPage();
            nativePage.navigate(url("/admin/race-scorings/" + race.getId() + "/edit"));
            assertThat(nativePage.locator("#racePointsHidden")).isVisible();
            assertThat(nativePage.locator("[data-points-controls]").first()).not().isVisible();
            nativePage.locator("#racePointsHidden").fill("30,20,10");
            nativePage.locator("#qualiPointsHidden").fill("2,1");
            nativePage.locator(".entity-editor-actions button").click();
            assertThat(nativePage).hasURL(url("/admin/race-scorings"));
        }
        var saved = raceRepository.findById(race.getId()).orElseThrow();
        assertEquals("30,20,10", saved.getRacePoints());
        assertEquals("2,1", saved.getQualiPoints());
    }

    @Test
    void givenMatchScoring_whenSaveInvalidName_thenResultPointsRemainEditable() {
        match = matchRepository.save(new MatchScoring("Test Editor Match " + UUID.randomUUID(), 3, 1, 0));
        page.navigate(url("/admin/match-scorings/" + match.getId() + "/edit"));
        page.locator("#pointsWin").fill("4");
        page.locator("#pointsDraw").fill("2");
        page.locator("#scoringForm").evaluate("form => form.noValidate = true");
        page.locator("#name").fill("");
        page.locator(".entity-editor-actions button").click();
        assertThat(page.locator("#name-error")).isVisible();
        assertThat(page.locator("#pointsWin")).hasValue("4");
        assertThat(page.locator("#pointsDraw")).hasValue("2");
        page.locator("#name").fill(match.getName());
        page.locator(".entity-editor-actions button").click();
        assertThat(page).hasURL(url("/admin/match-scorings"));
        assertEquals(4, matchRepository.findById(match.getId()).orElseThrow().getPointsWin());
        page.locator("[data-list-search]").fill(match.getName());
        assertThat(page.locator("[data-list-entry]:visible")).hasCount(1);
    }
}
