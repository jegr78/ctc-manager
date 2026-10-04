package org.ctc.e2e;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.ctc.TestHelper;
import org.ctc.domain.repository.RaceLineupRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@Tag("e2e")
class DetailWorkflowsE2ETest extends PlaywrightConfig {
    @Autowired TestHelper helper;
    @Autowired RaceLineupRepository lineups;

    @BeforeEach
    void setUp() { setupPage(); }

    @AfterEach
    void tearDown() { teardownPage(); }

    @Test
    void givenMatchWithRace_whenOpenLineupAndRaceTools_thenContextAndSectionNavigationRemainAvailable() {
        // given
        var fixture = helper.createFullSeasonFixture("Test Detail " + UUID.randomUUID().toString().substring(0, 8));
        page.navigate(url("/admin/matches/" + fixture.match().getId()));

        // when
        page.locator("#match-races a[href$='/lineup']").click();

        // then
        assertThat(page).hasURL(url("/admin/races/" + fixture.race().getId() + "/lineup"));
        assertThat(page.locator("nav[aria-label='Lineup teams']")).containsText(fixture.homeTeam().getShortName());

        // when
        page.locator(".toolbar a").click();
        page.locator("nav a[href='#race-graphics']").click();

        // then
        assertThat(page.locator("#race-graphics")).isVisible();
        assertThat(page.locator("#race-graphics button").first()).isDisabled();
        assertThat(page.locator("#race-graphics")).containsText("No lineup assigned.");
        assertThat(page.locator(".back-link")).hasAttribute("href", "/admin/races?matchdayId=" + fixture.matchday().getId());
        assertThat(page.locator(".event-maintenance")).not().hasAttribute("open", "");
        assertTrue((Boolean) page.evaluate("() => document.querySelector('#race-results').compareDocumentPosition(document.querySelector('#race-graphics')) & Node.DOCUMENT_POSITION_FOLLOWING ? true : false"));
    }

    @Test
    void givenRosterAndGuestDriver_whenRemoveAllRowsAddAndCorrectGuest_thenFocusLabelsAndSavedAssignmentsRemainValid() {
        // given
        var suffix = UUID.randomUUID().toString().substring(0, 8);
        var fixture = helper.createFullSeasonFixture("Test Guest Detail " + suffix);
        var roster = helper.createDriver("Test_Roster_" + suffix, "Test Roster " + suffix);
        var guest = helper.createDriver("Test_Guest_" + suffix, "Test Guest " + suffix);
        helper.createSeasonDriver(fixture.season(), roster, fixture.homeTeam());
        page.navigate(url("/admin/races/" + fixture.race().getId() + "/lineup"));
        var section = page.locator(".guest-section[data-team-id='" + fixture.homeTeam().getId() + "']");
        var add = section.locator(".guest-add-row");

        // when
        section.locator(".guest-remove").click();

        // then
        assertThat(add).isFocused();
        assertThat(section.locator(".guest-row")).hasCount(0);

        // when
        add.click();
        var input = section.locator(".guest-driver-input");

        // then
        assertThat(input).isFocused();
        assertThat(section.locator("label")).hasAttribute("for", input.getAttribute("id"));

        // when
        input.fill("Unknown Test Driver");
        page.locator(".entity-editor-actions button").click();

        // then
        assertThat(input).isFocused();
        assertTrue((Boolean) input.evaluate("el => !el.validity.valid"));
        assertEquals(0, lineups.findByRaceId(fixture.race().getId()).size());

        // when
        input.fill(guest.getPsnId() + " (" + guest.getNickname() + ")");
        page.locator("input[name='driver_" + roster.getId() + "']").check();
        page.locator(".entity-editor-actions button").click();

        // then
        assertThat(page.locator(".alert-success")).containsText("2 drivers assigned");
        var saved = lineups.findByRaceId(fixture.race().getId());
        assertEquals(2, saved.size());
        assertTrue(saved.stream().anyMatch(l -> l.isGuest() && l.getDriver().getId().equals(guest.getId())));
        assertThat(page.locator(".guest-section[data-team-id='" + fixture.homeTeam().getId() + "'] .guest-driver-input").first()).hasValue(guest.getPsnId() + " (" + guest.getNickname() + ")");
    }
}
