package de.ctc.e2e;

import org.junit.jupiter.api.*;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

class ScoringE2ETest extends PlaywrightConfig {

    @BeforeEach
    void setUp() {
        setupPage();
    }

    @AfterEach
    void tearDown() {
        teardownPage();
    }

    @Test
    void shouldShowScoringNavigationLinks() {
        page.navigate(url("/admin/race-scorings"));
        assertThat(page.getByRole(com.microsoft.playwright.options.AriaRole.LINK,
                new com.microsoft.playwright.Page.GetByRoleOptions().setName("Race-Scorings").setExact(true))).isVisible();
        assertThat(page.getByRole(com.microsoft.playwright.options.AriaRole.LINK,
                new com.microsoft.playwright.Page.GetByRoleOptions().setName("Match-Scorings").setExact(true))).isVisible();
    }

    @Test
    void shouldListRaceScorings() {
        page.navigate(url("/admin/race-scorings"));
        assertThat(page).hasTitle("CTC Admin - Race-Scorings");
        assertThat(page.locator("h1")).containsText("Race-Scorings");
        // DevDataSeeder creates "CTC Standard"
        assertThat(page.locator("table")).containsText("CTC Standard");
    }

    @Test
    void shouldCreateRaceScoring() {
        page.navigate(url("/admin/race-scorings/new"));
        assertThat(page.locator("h1")).containsText("Neues Race-Scoring");

        page.fill("#name", "E2E Test Scoring");
        // Add 3 race point positions
        page.click("text=+ Hinzufuegen");
        page.click("text=+ Hinzufuegen");
        page.click("text=+ Hinzufuegen");

        // Set values in race points inputs
        var raceInputs = page.locator("#racePointsRows input[type=number]");
        raceInputs.nth(0).fill("15");
        raceInputs.nth(1).fill("10");
        raceInputs.nth(2).fill("5");

        page.fill("#fastestLapPoints", "1");
        page.click("text=Speichern");

        assertThat(page.locator(".alert-success")).containsText("Race-Scoring saved");
        assertThat(page.locator("table")).containsText("E2E Test Scoring");
    }

    @Test
    void shouldListMatchScorings() {
        page.navigate(url("/admin/match-scorings"));
        assertThat(page).hasTitle("CTC Admin - Match-Scorings");
        assertThat(page.locator("h1")).containsText("Match-Scorings");
        assertThat(page.locator("table")).containsText("Standard 3-1-0");
    }

    @Test
    void shouldCreateMatchScoring() {
        page.navigate(url("/admin/match-scorings/new"));
        assertThat(page.locator("h1")).containsText("Neues Match-Scoring");

        page.fill("#name", "E2E Match 2-1-0");
        page.fill("#pointsWin", "2");
        page.fill("#pointsDraw", "1");
        page.fill("#pointsLoss", "0");
        page.click("text=Speichern");

        assertThat(page.locator(".alert-success")).containsText("Match-Scoring saved");
        assertThat(page.locator("table")).containsText("E2E Match 2-1-0");
    }

    @Test
    void shouldRejectInvalidRaceScoringMonotonicity() {
        page.navigate(url("/admin/race-scorings/new"));
        page.fill("#name", "Invalid Scoring");

        // Add positions with non-monotonic values: 10, 20, 5
        page.click("text=+ Hinzufuegen");
        page.click("text=+ Hinzufuegen");
        page.click("text=+ Hinzufuegen");

        var raceInputs = page.locator("#racePointsRows input[type=number]");
        raceInputs.nth(0).fill("10");
        raceInputs.nth(1).fill("20");
        raceInputs.nth(2).fill("5");

        page.fill("#fastestLapPoints", "0");
        page.click("text=Speichern");

        assertThat(page.locator(".alert-error")).containsText("monoton fallend");
    }
}
