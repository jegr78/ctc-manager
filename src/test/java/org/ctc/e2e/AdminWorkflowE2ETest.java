package org.ctc.e2e;

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
    void whenNavigateToRoot_thenRedirectsToSeasons() {
        // when
        page.navigate(url("/"));

        // then
        assertThat(page).hasTitle("CTC Admin - Seasons");
        assertThat(page.locator("h1")).containsText("Seasons");
    }

    @Test
    void whenNavigateToSeasons_thenAllNavigationLinksAreVisible() {
        // when
        page.navigate(url("/admin/seasons"));

        // then
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
    void givenSeasonForm_whenSaveWithValidData_thenSeasonAppearsInList() {
        // given
        page.navigate(url("/admin/seasons/new"));

        // when
        page.getByRole(com.microsoft.playwright.options.AriaRole.TEXTBOX, new com.microsoft.playwright.Page.GetByRoleOptions().setName("Name").setExact(true)).fill("E2E Season");
        page.fill("#year", "2026");
        page.fill("#number", "99");
        // Select scoring presets (created by DevDataSeeder)
        page.selectOption("#raceScoring", new com.microsoft.playwright.options.SelectOption().setLabel("CTC Standard"));
        page.selectOption("#matchScoring", new com.microsoft.playwright.options.SelectOption().setLabel("Standard 3-1-0"));
        page.getByRole(com.microsoft.playwright.options.AriaRole.BUTTON, new com.microsoft.playwright.Page.GetByRoleOptions().setName("Save")).click();

        // then
        assertThat(page).hasTitle("CTC Admin - Seasons");
        assertThat(page.getByRole(com.microsoft.playwright.options.AriaRole.CELL, new com.microsoft.playwright.Page.GetByRoleOptions().setName("E2E Season"))).isVisible();
        assertThat(page.locator(".alert-success")).containsText("Season saved");
    }

    @Test
    void givenTeamForm_whenSaveWithValidData_thenTeamAppearsInList() {
        // given
        page.navigate(url("/admin/teams/new"));

        // when
        page.getByRole(com.microsoft.playwright.options.AriaRole.TEXTBOX, new com.microsoft.playwright.Page.GetByRoleOptions().setName("Name").setExact(true)).fill("Test Racing");
        page.getByRole(com.microsoft.playwright.options.AriaRole.TEXTBOX, new com.microsoft.playwright.Page.GetByRoleOptions().setName("Short Name")).fill("TST");
        page.getByRole(com.microsoft.playwright.options.AriaRole.BUTTON, new com.microsoft.playwright.Page.GetByRoleOptions().setName("Save")).click();

        // then
        assertThat(page).hasTitle("CTC Admin - Teams");
        assertThat(page.locator("text=TST")).isVisible();
        assertThat(page.locator(".alert-success")).containsText("Team saved");
    }

    @Test
    void givenDriverForm_whenSaveWithValidData_thenDriverAppearsInTable() {
        // given
        page.navigate(url("/admin/drivers/new"));

        // when
        page.getByRole(com.microsoft.playwright.options.AriaRole.TEXTBOX, new com.microsoft.playwright.Page.GetByRoleOptions().setName("PSN ID")).fill("e2e_driver");
        page.getByRole(com.microsoft.playwright.options.AriaRole.TEXTBOX, new com.microsoft.playwright.Page.GetByRoleOptions().setName("Nickname")).fill("E2E Driver");
        page.getByRole(com.microsoft.playwright.options.AriaRole.BUTTON, new com.microsoft.playwright.Page.GetByRoleOptions().setName("Save")).click();

        // then
        assertThat(page).hasTitle("CTC Admin - Drivers");
        assertThat(page.locator(".alert-success")).containsText("Driver saved");
        // Driver appears in paginated table — check DOM presence (may be hidden by pagination JS)
        assertThat(page.locator("table")).containsText("e2e_driver");
    }

    @Test
    void whenNavigateToStandings_thenPageIsVisible() {
        // when
        page.navigate(url("/admin/standings"));

        // then
        assertThat(page.locator("h1")).containsText("Standings");
    }

    @Test
    void whenNavigateToImport_thenPageIsVisible() {
        // when
        page.navigate(url("/admin/import"));

        // then
        assertThat(page.locator("h1")).containsText("Import");
    }

    @Test
    void whenNavigateToGenerate_thenPageShowsGenerateButton() {
        // when
        page.navigate(url("/admin/generate"));

        // then
        assertThat(page.locator("h1")).containsText("Generate Static Website");
        assertThat(page.getByRole(com.microsoft.playwright.options.AriaRole.BUTTON, new com.microsoft.playwright.Page.GetByRoleOptions().setName("Generate Site"))).isVisible();
    }

    @Test
    void givenSeasonsPage_whenNavigatingSidebarLinks_thenCorrectPagesLoad() {
        // given
        page.navigate(url("/admin/seasons"));

        // when / then
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
    void givenCarForm_whenSaveWithValidData_thenCarAppearsInList() {
        // given
        page.navigate(url("/admin/cars/new"));

        // when
        page.getByRole(com.microsoft.playwright.options.AriaRole.TEXTBOX, new com.microsoft.playwright.Page.GetByRoleOptions().setName("Manufacturer")).fill("Mazda");
        page.getByRole(com.microsoft.playwright.options.AriaRole.TEXTBOX, new com.microsoft.playwright.Page.GetByRoleOptions().setName("Name").setExact(true)).fill("RX-Vision GT3");
        page.getByRole(com.microsoft.playwright.options.AriaRole.BUTTON, new com.microsoft.playwright.Page.GetByRoleOptions().setName("Save")).click();

        // then
        assertThat(page).hasTitle("CTC Admin - Cars");
        assertThat(page.locator(".alert-success")).containsText("Car saved");
        assertThat(page.locator("table")).containsText("Mazda");
    }

    @Test
    void givenTestAlphaTeam_whenViewTeamDetail_thenShowsDriversGroupedBySeason() {
        // given
        // T-ALF (Test Alpha Racing) hat Fahrer in Test-Season 2026 und 2025
        page.navigate(url("/admin/teams"));

        // when
        page.locator("a:has-text('T-ALF')").first().click();

        // then
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
    void givenTestBravoTeamWithSubTeams_whenViewTeamDetail_thenShowsSubTeamDriverGroups() {
        // given
        // T-BRV (Test Bravo Racing) hat Sub-Teams T-BRV 1 + T-BRV 2
        page.navigate(url("/admin/teams"));

        // when
        page.locator("a:has-text('T-BRV')").first().click();

        // then
        assertThat(page.locator("h1")).containsText("Test Bravo Racing");

        page.locator(".season-header:has-text('Test-Season 2026')").click();

        assertThat(page.locator(".team-group-label").first()).isVisible();
        assertThat(page.locator(".badge-sub").first()).isVisible();
        assertThat(page.locator(".chip").first()).isVisible();
    }

    @Test
    void givenTestAlphaTeamWithMultipleSeasons_whenViewTeamDetail_thenShowsMultipleSeasonAccordions() {
        // given
        // T-ALF hat Lineups in Test-Season 2026 + Test-Season 2025
        page.navigate(url("/admin/teams"));

        // when
        page.locator("a:has-text('T-ALF')").first().click();

        // then
        var allAccordions = page.locator("details.season-accordion");
        assertThat(allAccordions.first()).isVisible();

        org.junit.jupiter.api.Assertions.assertTrue(allAccordions.count() >= 2,
                "Expected at least 2 season accordions, got " + allAccordions.count());

        assertThat(page.locator("text=Test-Season 2026")).isVisible();
        assertThat(page.locator("text=Test-Season 2025")).isVisible();
    }

    @Test
    void givenTemplateEditor_whenSaveTemplateAndGenerateAll_thenCardsAreGenerated() {
        // given
        // Navigate to template editor
        page.navigate(url("/admin/tools/template-editors?tab=team-cards"));
        assertThat(page.locator("h1")).containsText("Template Editors");

        // when
        // Save the template (default content, just hit save)
        page.getByRole(com.microsoft.playwright.options.AriaRole.BUTTON,
                new com.microsoft.playwright.Page.GetByRoleOptions().setName("Save Template")).click();

        // then
        assertThat(page.locator(".alert-success")).containsText("Team card template saved");

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
        page.navigate(url("/admin/tools/template-editors?tab=team-cards"));
        page.onDialog(dialog -> dialog.accept());
        page.getByRole(com.microsoft.playwright.options.AriaRole.BUTTON,
                new com.microsoft.playwright.Page.GetByRoleOptions().setName("Reset")).click();
        assertThat(page.locator(".alert-success")).containsText("Team card template reset");
    }

    @Test
    void whenNavigateToGt7Sync_thenPageShowsFetchButton() {
        // when
        page.navigate(url("/admin/gt7-sync"));

        // then
        assertThat(page.locator("h1")).containsText("GT7 Sync");
        assertThat(page.getByRole(com.microsoft.playwright.options.AriaRole.BUTTON,
                new com.microsoft.playwright.Page.GetByRoleOptions().setName("Fetch & Compare"))).isVisible();
    }

    @Test
    void givenTrackForm_whenSaveWithValidData_thenTrackAppearsInList() {
        // given
        page.navigate(url("/admin/tracks/new"));

        // when
        page.getByRole(com.microsoft.playwright.options.AriaRole.TEXTBOX, new com.microsoft.playwright.Page.GetByRoleOptions().setName("Name").setExact(true)).fill("Tsukuba Circuit");
        page.getByRole(com.microsoft.playwright.options.AriaRole.TEXTBOX, new com.microsoft.playwright.Page.GetByRoleOptions().setName("Country")).fill("Japan");
        page.getByRole(com.microsoft.playwright.options.AriaRole.BUTTON, new com.microsoft.playwright.Page.GetByRoleOptions().setName("Save")).click();

        // then
        assertThat(page).hasTitle("CTC Admin - Tracks");
        assertThat(page.locator(".alert-success")).containsText("Track saved");
        assertThat(page.locator("table")).containsText("Tsukuba Circuit");
    }
}
