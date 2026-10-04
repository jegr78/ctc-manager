package org.ctc.e2e;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.time.LocalDate;
import java.util.UUID;
import org.ctc.TestHelper;
import org.ctc.domain.repository.PlayoffRepository;
import org.ctc.domain.service.PlayoffSeedingService;
import org.ctc.domain.service.PlayoffService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@Tag("e2e")
class PlayoffEditorsE2ETest extends PlaywrightConfig {
    @Autowired TestHelper helper;
    @Autowired PlayoffRepository playoffs;
    @Autowired PlayoffService playoffService;
    @Autowired PlayoffSeedingService seeding;

    @BeforeEach
    void setUp() { setupPage(); }

    @AfterEach
    void tearDown() { teardownPage(); }

    @Test
    void givenNewPlayoff_whenBlankNameIsCorrected_thenScheduleRemainsAndBracketIsCreated() {
        // given
        var fixture = helper.createFullSeasonFixture("Test Playoff Editor " + UUID.randomUUID().toString().substring(0, 8));
        var seasonId = fixture.season().getId();
        page.navigate(url("/admin/playoffs/new?seasonId=" + seasonId));
        page.locator("#numberOfTeams").selectOption("4");
        page.locator("#startDate").fill("2028-01-01");
        page.locator("#endDate").fill("2028-06-30");
        page.locator("#eventDurationMinutes").fill("90");

        // when
        page.locator(".entity-editor-form").evaluate("form => form.noValidate = true");
        page.locator(".entity-editor-actions button").click();

        // then
        assertThat(page.locator("#name-error")).isVisible();
        assertThat(page.locator("#name")).hasAttribute("aria-invalid", "true");
        assertThat(page.locator("#seasonId")).hasValue(seasonId.toString());
        assertThat(page.locator("#startDate")).hasValue("2028-01-01");
        assertThat(page.locator("#endDate")).hasValue("2028-06-30");
        assertThat(page.locator("#numberOfTeams")).hasValue("4");
        assertThat(page.locator(".entity-editor-actions a")).hasAttribute("href", "/admin/playoffs?seasonId=" + seasonId);

        // when
        page.locator("#name").fill("Test Editor Playoffs");
        page.locator(".entity-editor-actions button").click();

        // then
        assertThat(page).hasURL(url("/admin/playoffs?seasonId=" + seasonId));
        var saved = playoffs.findBySeasonId(seasonId).orElseThrow();
        assertEquals(LocalDate.of(2028, 1, 1), saved.getStartDate());
        assertEquals(LocalDate.of(2028, 6, 30), saved.getEndDate());
        assertEquals(90, saved.getEventDurationMinutes());
    }

    @Test
    void givenMobileSeeding_whenTeamIsSavedThenMatchupIsDecided_thenAssignmentsPersistAndControlsAreFrozen() {
        // given
        var fixture = helper.createFullSeasonFixture("Test Seeding Editor " + UUID.randomUUID().toString().substring(0, 8));
        var playoff = helper.createPlayoffInPhase(fixture.season(), "Test Editor Seeding", 4);
        var path = "/admin/playoffs/" + playoff.getId() + "/seed";
        page.setViewportSize(390, 768);
        page.navigate(url(path));

        // then
        assertFalse((Boolean) page.evaluate("document.documentElement.scrollWidth > innerWidth"));
        assertThat(page.getByLabel("Matchup 1 · Team 1", new com.microsoft.playwright.Page.GetByLabelOptions().setExact(true))).isVisible();
        assertThat(page.locator("#automatic-seeding")).containsText("Save edited seed numbers above");

        // when
        page.locator("#seed-team-0").selectOption(fixture.homeTeam().getId().toString());
        page.locator("#seed-number-0").fill("1");
        page.locator(".entity-editor-actions button").click();
        page.navigate(url(path));

        // then
        assertThat(page.locator("#seed-team-0")).hasValue(fixture.homeTeam().getId().toString());
        assertThat(page.locator("#seed-number-0")).hasValue("1");
        assertThat(page.locator("#seed-team-1")).hasValue("");

        // when
        var matchupId = seeding.getSeedingData(playoff.getId()).firstRound().getMatchups().getFirst().getId();
        playoffService.declareBye(matchupId);
        page.reload();

        // then
        assertThat(page.locator("[data-testid='seeding-frozen']")).isVisible();
        assertThat(page.locator("#seed-team-0")).isDisabled();
        assertThat(page.locator("#seed-number-0")).isDisabled();
        assertThat(page.locator(".entity-editor-actions button")).hasCount(0);
        assertThat(page.locator("#automatic-seeding")).hasCount(0);
    }
    @Test
    void givenReadyMatchup_whenAddingLegWithoutJavaScript_thenRaceIsCreatedAndLinkedForConfiguration() {
        var fixture = helper.createFullSeasonFixture("Test Leg Editor " + UUID.randomUUID().toString().substring(0, 8));
        var playoff = helper.createPlayoffInPhase(fixture.season(), "Test Leg Preparation", 4);
        var matchupId = playoff.getRounds().getFirst().getMatchups().getFirst().getId();
        seeding.seedTeam(matchupId, fixture.homeTeam().getId(), 1);
        seeding.seedTeam(matchupId, fixture.awayTeam().getId(), 2);
        try (var context = browser.newContext(new com.microsoft.playwright.Browser.NewContextOptions()
                .setJavaScriptEnabled(false).setViewportSize(390, 768))) {
            var nativePage = context.newPage();
            nativePage.navigate(url("/admin/playoffs/matchup/" + matchupId));
            assertThat(nativePage.locator("input[name='track'], input[name='car'], input[name='dateTime']")).hasCount(0);
            nativePage.getByRole(com.microsoft.playwright.options.AriaRole.BUTTON,
                    new com.microsoft.playwright.Page.GetByRoleOptions().setName("Add Leg").setExact(true)).click();
            assertThat(nativePage.locator(".alert-success")).containsText("Leg added");
            assertThat(nativePage.locator("tbody tr")).hasCount(1);
            var legs = playoffService.getMatchupDetail(matchupId).legs();
            assertEquals(1, legs.size());
            assertThat(nativePage.getByRole(com.microsoft.playwright.options.AriaRole.LINK,
                    new com.microsoft.playwright.Page.GetByRoleOptions().setName("View leg 1").setExact(true)))
                    .hasAttribute("href", "/admin/races/" + legs.getFirst().getId());
            assertFalse((Boolean) nativePage.evaluate("document.documentElement.scrollWidth > innerWidth"));
        }
    }

}
