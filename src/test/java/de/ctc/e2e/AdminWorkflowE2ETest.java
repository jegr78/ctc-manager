package de.ctc.e2e;

import org.junit.jupiter.api.*;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

class AdminWorkflowE2ETest extends PlaywrightConfig {

    @BeforeEach
    void setUp() {
        setupPage();
    }

    @AfterEach
    void tearDown() {
        teardownPage();
    }

    @Test
    void shouldRedirectRootToSeasons() {
        page.navigate(url("/"));
        assertThat(page).hasTitle("CTC Admin - Seasons");
        assertThat(page.locator("h1")).containsText("Seasons");
    }

    @Test
    void shouldShowAllNavigationLinks() {
        page.navigate(url("/admin/seasons"));

        assertThat(page.getByRole(com.microsoft.playwright.options.AriaRole.LINK, new com.microsoft.playwright.Page.GetByRoleOptions().setName("Seasons").setExact(true))).isVisible();
        assertThat(page.getByRole(com.microsoft.playwright.options.AriaRole.LINK, new com.microsoft.playwright.Page.GetByRoleOptions().setName("Teams").setExact(true))).isVisible();
        assertThat(page.getByRole(com.microsoft.playwright.options.AriaRole.LINK, new com.microsoft.playwright.Page.GetByRoleOptions().setName("Drivers").setExact(true))).isVisible();
        assertThat(page.getByRole(com.microsoft.playwright.options.AriaRole.LINK, new com.microsoft.playwright.Page.GetByRoleOptions().setName("Matchdays").setExact(true))).isVisible();
        assertThat(page.getByRole(com.microsoft.playwright.options.AriaRole.LINK, new com.microsoft.playwright.Page.GetByRoleOptions().setName("Races").setExact(true))).isVisible();
        assertThat(page.getByRole(com.microsoft.playwright.options.AriaRole.LINK, new com.microsoft.playwright.Page.GetByRoleOptions().setName("Import").setExact(true))).isVisible();
        assertThat(page.getByRole(com.microsoft.playwright.options.AriaRole.LINK, new com.microsoft.playwright.Page.GetByRoleOptions().setName("Standings").setExact(true))).isVisible();
        assertThat(page.getByRole(com.microsoft.playwright.options.AriaRole.LINK, new com.microsoft.playwright.Page.GetByRoleOptions().setName("Generate Site"))).isVisible();
        assertThat(page.getByRole(com.microsoft.playwright.options.AriaRole.LINK, new com.microsoft.playwright.Page.GetByRoleOptions().setName("Cars").setExact(true))).isVisible();
        assertThat(page.getByRole(com.microsoft.playwright.options.AriaRole.LINK, new com.microsoft.playwright.Page.GetByRoleOptions().setName("Tracks").setExact(true))).isVisible();
        assertThat(page.getByRole(com.microsoft.playwright.options.AriaRole.LINK, new com.microsoft.playwright.Page.GetByRoleOptions().setName("GT7 Sync"))).isVisible();
        assertThat(page.getByRole(com.microsoft.playwright.options.AriaRole.LINK, new com.microsoft.playwright.Page.GetByRoleOptions().setName("Race-Scorings"))).isVisible();
        assertThat(page.getByRole(com.microsoft.playwright.options.AriaRole.LINK, new com.microsoft.playwright.Page.GetByRoleOptions().setName("Match-Scorings"))).isVisible();
    }

    @Test
    void shouldCreateSeasonAndShowInList() {
        page.navigate(url("/admin/seasons/new"));
        page.getByRole(com.microsoft.playwright.options.AriaRole.TEXTBOX, new com.microsoft.playwright.Page.GetByRoleOptions().setName("Name").setExact(true)).fill("E2E Season");
        // Select scoring presets (created by DevDataSeeder)
        page.selectOption("#raceScoring", new com.microsoft.playwright.options.SelectOption().setLabel("CTC Standard"));
        page.selectOption("#matchScoring", new com.microsoft.playwright.options.SelectOption().setLabel("Standard 3-1-0"));
        page.getByRole(com.microsoft.playwright.options.AriaRole.BUTTON, new com.microsoft.playwright.Page.GetByRoleOptions().setName("Save")).click();

        assertThat(page).hasTitle("CTC Admin - Seasons");
        assertThat(page.getByRole(com.microsoft.playwright.options.AriaRole.CELL, new com.microsoft.playwright.Page.GetByRoleOptions().setName("E2E Season"))).isVisible();
        assertThat(page.locator(".alert-success")).containsText("Season saved");
    }

    @Test
    void shouldCreateTeamAndShowInList() {
        page.navigate(url("/admin/teams/new"));
        page.getByRole(com.microsoft.playwright.options.AriaRole.TEXTBOX, new com.microsoft.playwright.Page.GetByRoleOptions().setName("Name").setExact(true)).fill("Test Racing");
        page.getByRole(com.microsoft.playwright.options.AriaRole.TEXTBOX, new com.microsoft.playwright.Page.GetByRoleOptions().setName("Short Name")).fill("TST");
        page.getByRole(com.microsoft.playwright.options.AriaRole.BUTTON, new com.microsoft.playwright.Page.GetByRoleOptions().setName("Save")).click();

        assertThat(page).hasTitle("CTC Admin - Teams");
        assertThat(page.locator("text=TST")).isVisible();
        assertThat(page.locator(".alert-success")).containsText("Team saved");
    }

    @Test
    void shouldCreateDriverAndShowInList() {
        page.navigate(url("/admin/drivers/new"));
        page.getByRole(com.microsoft.playwright.options.AriaRole.TEXTBOX, new com.microsoft.playwright.Page.GetByRoleOptions().setName("PSN ID")).fill("e2e_driver");
        page.getByRole(com.microsoft.playwright.options.AriaRole.TEXTBOX, new com.microsoft.playwright.Page.GetByRoleOptions().setName("Nickname")).fill("E2E Driver");
        page.getByRole(com.microsoft.playwright.options.AriaRole.BUTTON, new com.microsoft.playwright.Page.GetByRoleOptions().setName("Save")).click();

        assertThat(page).hasTitle("CTC Admin - Drivers");
        assertThat(page.locator(".alert-success")).containsText("Driver saved");
        // Driver appears in paginated table — check DOM presence (may be hidden by pagination JS)
        assertThat(page.locator("table")).containsText("e2e_driver");
    }

    @Test
    void shouldShowEmptyStandings() {
        page.navigate(url("/admin/standings"));
        assertThat(page.locator("h1")).containsText("Standings");
    }

    @Test
    void shouldShowImportPage() {
        page.navigate(url("/admin/import"));
        assertThat(page.locator("h1")).containsText("Import");
    }

    @Test
    void shouldShowGeneratePage() {
        page.navigate(url("/admin/generate"));
        assertThat(page.locator("h1")).containsText("Generate Static Website");
        assertThat(page.getByRole(com.microsoft.playwright.options.AriaRole.BUTTON, new com.microsoft.playwright.Page.GetByRoleOptions().setName("Generate Site"))).isVisible();
    }

    @Test
    void shouldNavigateAllPages() {
        page.navigate(url("/admin/seasons"));

        page.getByRole(com.microsoft.playwright.options.AriaRole.LINK, new com.microsoft.playwright.Page.GetByRoleOptions().setName("Teams").setExact(true)).first().click();
        assertThat(page.locator("h1")).containsText("Teams");

        page.getByRole(com.microsoft.playwright.options.AriaRole.LINK, new com.microsoft.playwright.Page.GetByRoleOptions().setName("Drivers").setExact(true)).first().click();
        assertThat(page.locator("h1")).containsText("Drivers");

        page.getByRole(com.microsoft.playwright.options.AriaRole.LINK, new com.microsoft.playwright.Page.GetByRoleOptions().setName("Matchdays").setExact(true)).first().click();
        assertThat(page.locator("h1")).containsText("Matchdays");

        page.getByRole(com.microsoft.playwright.options.AriaRole.LINK, new com.microsoft.playwright.Page.GetByRoleOptions().setName("Races").setExact(true)).first().click();
        assertThat(page.locator("h1")).containsText("Races");

        page.getByRole(com.microsoft.playwright.options.AriaRole.LINK, new com.microsoft.playwright.Page.GetByRoleOptions().setName("Cars").setExact(true)).first().click();
        assertThat(page.locator("h1")).containsText("Cars");

        page.getByRole(com.microsoft.playwright.options.AriaRole.LINK, new com.microsoft.playwright.Page.GetByRoleOptions().setName("Tracks").setExact(true)).first().click();
        assertThat(page.locator("h1")).containsText("Tracks");
    }

    @Test
    void shouldCreateCarAndShowInList() {
        page.navigate(url("/admin/cars/new"));
        page.getByRole(com.microsoft.playwright.options.AriaRole.TEXTBOX, new com.microsoft.playwright.Page.GetByRoleOptions().setName("Manufacturer")).fill("Mazda");
        page.getByRole(com.microsoft.playwright.options.AriaRole.TEXTBOX, new com.microsoft.playwright.Page.GetByRoleOptions().setName("Name").setExact(true)).fill("RX-Vision GT3");
        page.getByRole(com.microsoft.playwright.options.AriaRole.BUTTON, new com.microsoft.playwright.Page.GetByRoleOptions().setName("Save")).click();

        assertThat(page).hasTitle("CTC Admin - Cars");
        assertThat(page.locator(".alert-success")).containsText("Car saved");
        assertThat(page.locator("table")).containsText("Mazda");
    }

    @Test
    void shouldShowTeamDetailWithDriversGroupedBySeason() {
        // T-ALF (Test Alpha Racing) hat Fahrer in Test-Season 2026 und 2025
        page.navigate(url("/admin/teams"));
        page.locator("a:has-text('T-ALF')").first().click();

        assertThat(page.locator("h1")).containsText("Test Alpha Racing");
        assertThat(page.locator("h2:has-text('Seasons & Drivers')")).isVisible();

        var seasonHeader = page.locator(".season-header:has-text('Test-Season 2026')").first();
        assertThat(seasonHeader).isVisible();
        assertThat(seasonHeader).containsText("Drivers");

        // Fahrer im DOM vorhanden (Accordion muss nicht offen sein)
        assertThat(page.locator("details.season-accordion:has-text('Test-Season 2026')")).containsText("Test_Alpha_1");
        assertThat(page.locator("details.season-accordion:has-text('Test-Season 2026')")).containsText("Test_Alpha_2");
    }

    @Test
    void shouldShowTeamDetailWithSubTeamDriverGroups() {
        // T-BRV (Test Bravo Racing) hat Sub-Teams T-BRV 1 + T-BRV 2
        page.navigate(url("/admin/teams"));
        page.locator("a:has-text('T-BRV')").first().click();

        assertThat(page.locator("h1")).containsText("Test Bravo Racing");

        page.locator(".season-header:has-text('Test-Season 2026')").click();

        assertThat(page.locator(".team-group-label").first()).isVisible();
        assertThat(page.locator(".badge-sub").first()).isVisible();
        assertThat(page.locator(".chip").first()).isVisible();
    }

    @Test
    void shouldShowMultipleSeasonAccordions() {
        // T-ALF hat Lineups in Test-Season 2026 + Test-Season 2025
        page.navigate(url("/admin/teams"));
        page.locator("a:has-text('T-ALF')").first().click();

        var allAccordions = page.locator("details.season-accordion");
        assertThat(allAccordions.first()).isVisible();

        org.junit.jupiter.api.Assertions.assertTrue(allAccordions.count() >= 2,
                "Expected at least 2 season accordions, got " + allAccordions.count());

        assertThat(page.locator("text=Test-Season 2026")).isVisible();
        assertThat(page.locator("text=Test-Season 2025")).isVisible();
    }

    @Test
    void shouldSaveTemplateAndGenerateAllCards() {
        // Navigate to template editor
        page.navigate(url("/admin/tools/team-cards/template"));
        assertThat(page.locator("h1")).containsText("Card Template Editor");

        // Save the template (default content, just hit save)
        page.getByRole(com.microsoft.playwright.options.AriaRole.BUTTON,
                new com.microsoft.playwright.Page.GetByRoleOptions().setName("Save Template")).click();
        assertThat(page.locator(".alert-success")).containsText("Template saved");

        // Navigate to team cards page (active season auto-selected)
        page.navigate(url("/admin/tools/team-cards"));
        assertThat(page.locator("h1")).containsText("Team Cards");

        // Click Generate All
        page.getByRole(com.microsoft.playwright.options.AriaRole.BUTTON,
                new com.microsoft.playwright.Page.GetByRoleOptions().setName("Generate All")).click();

        // Should redirect back with success message
        assertThat(page.locator(".alert-success")).containsText("cards generated");

        // At least one card thumbnail should be visible (img in the grid)
        assertThat(page.locator(".card img").first()).isVisible();

        // Reset template back to default (confirm dialog)
        page.navigate(url("/admin/tools/team-cards/template"));
        page.onDialog(dialog -> dialog.accept());
        page.getByRole(com.microsoft.playwright.options.AriaRole.BUTTON,
                new com.microsoft.playwright.Page.GetByRoleOptions().setName("Reset")).click();
        assertThat(page.locator(".alert-success")).containsText("Template reset");
    }

    @Test
    void shouldShowGt7SyncPage() {
        page.navigate(url("/admin/gt7-sync"));
        assertThat(page.locator("h1")).containsText("GT7 Sync");
        assertThat(page.getByRole(com.microsoft.playwright.options.AriaRole.BUTTON,
                new com.microsoft.playwright.Page.GetByRoleOptions().setName("Fetch & Compare"))).isVisible();
    }

    @Test
    void shouldCreateTrackAndShowInList() {
        page.navigate(url("/admin/tracks/new"));
        page.getByRole(com.microsoft.playwright.options.AriaRole.TEXTBOX, new com.microsoft.playwright.Page.GetByRoleOptions().setName("Name").setExact(true)).fill("Tsukuba Circuit");
        page.getByRole(com.microsoft.playwright.options.AriaRole.TEXTBOX, new com.microsoft.playwright.Page.GetByRoleOptions().setName("Country")).fill("Japan");
        page.getByRole(com.microsoft.playwright.options.AriaRole.BUTTON, new com.microsoft.playwright.Page.GetByRoleOptions().setName("Save")).click();

        assertThat(page).hasTitle("CTC Admin - Tracks");
        assertThat(page.locator(".alert-success")).containsText("Track saved");
        assertThat(page.locator("table")).containsText("Tsukuba Circuit");
    }
}
