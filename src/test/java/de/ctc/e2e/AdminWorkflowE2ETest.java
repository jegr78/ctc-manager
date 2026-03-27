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
        assertThat(page).hasTitle("CTC Admin - Saisons");
        assertThat(page.locator("h1")).containsText("Saisons");
    }

    @Test
    void shouldShowAllNavigationLinks() {
        page.navigate(url("/admin/seasons"));

        assertThat(page.getByRole(com.microsoft.playwright.options.AriaRole.LINK, new com.microsoft.playwright.Page.GetByRoleOptions().setName("Saisons"))).isVisible();
        assertThat(page.getByRole(com.microsoft.playwright.options.AriaRole.LINK, new com.microsoft.playwright.Page.GetByRoleOptions().setName("Teams"))).isVisible();
        assertThat(page.getByRole(com.microsoft.playwright.options.AriaRole.LINK, new com.microsoft.playwright.Page.GetByRoleOptions().setName("Fahrer"))).isVisible();
        assertThat(page.getByRole(com.microsoft.playwright.options.AriaRole.LINK, new com.microsoft.playwright.Page.GetByRoleOptions().setName("Spieltage"))).isVisible();
        assertThat(page.getByRole(com.microsoft.playwright.options.AriaRole.LINK, new com.microsoft.playwright.Page.GetByRoleOptions().setName("Rennen"))).isVisible();
        assertThat(page.getByRole(com.microsoft.playwright.options.AriaRole.LINK, new com.microsoft.playwright.Page.GetByRoleOptions().setName("Import"))).isVisible();
        assertThat(page.getByRole(com.microsoft.playwright.options.AriaRole.LINK, new com.microsoft.playwright.Page.GetByRoleOptions().setName("Tabelle"))).isVisible();
        assertThat(page.getByRole(com.microsoft.playwright.options.AriaRole.LINK, new com.microsoft.playwright.Page.GetByRoleOptions().setName("Seite generieren"))).isVisible();
    }

    @Test
    void shouldCreateSeasonAndShowInList() {
        page.navigate(url("/admin/seasons/new"));
        page.getByRole(com.microsoft.playwright.options.AriaRole.TEXTBOX, new com.microsoft.playwright.Page.GetByRoleOptions().setName("Name")).fill("E2E Season");
        page.getByRole(com.microsoft.playwright.options.AriaRole.BUTTON, new com.microsoft.playwright.Page.GetByRoleOptions().setName("Speichern")).click();

        assertThat(page).hasTitle("CTC Admin - Saisons");
        assertThat(page.getByRole(com.microsoft.playwright.options.AriaRole.CELL, new com.microsoft.playwright.Page.GetByRoleOptions().setName("E2E Season"))).isVisible();
        assertThat(page.locator(".alert-success")).containsText("Saison gespeichert");
    }

    @Test
    void shouldCreateTeamAndShowInList() {
        page.navigate(url("/admin/teams/new"));
        page.getByRole(com.microsoft.playwright.options.AriaRole.TEXTBOX, new com.microsoft.playwright.Page.GetByRoleOptions().setName("Name")).fill("Test Racing");
        page.getByRole(com.microsoft.playwright.options.AriaRole.TEXTBOX, new com.microsoft.playwright.Page.GetByRoleOptions().setName("Kürzel")).fill("TST");
        page.getByRole(com.microsoft.playwright.options.AriaRole.BUTTON, new com.microsoft.playwright.Page.GetByRoleOptions().setName("Speichern")).click();

        assertThat(page).hasTitle("CTC Admin - Teams");
        assertThat(page.locator("text=TST")).isVisible();
        assertThat(page.locator("text=Team gespeichert")).isVisible();
    }

    @Test
    void shouldCreateDriverAndShowInList() {
        page.navigate(url("/admin/drivers/new"));
        page.getByRole(com.microsoft.playwright.options.AriaRole.TEXTBOX, new com.microsoft.playwright.Page.GetByRoleOptions().setName("PSN ID")).fill("e2e_driver");
        page.getByRole(com.microsoft.playwright.options.AriaRole.TEXTBOX, new com.microsoft.playwright.Page.GetByRoleOptions().setName("Nickname")).fill("E2E Driver");
        page.getByRole(com.microsoft.playwright.options.AriaRole.BUTTON, new com.microsoft.playwright.Page.GetByRoleOptions().setName("Speichern")).click();

        assertThat(page).hasTitle("CTC Admin - Fahrer");
        assertThat(page.getByRole(com.microsoft.playwright.options.AriaRole.CELL, new com.microsoft.playwright.Page.GetByRoleOptions().setName("e2e_driver").setExact(true))).isVisible();
        assertThat(page.locator(".alert-success")).containsText("Fahrer gespeichert");
    }

    @Test
    void shouldShowEmptyStandings() {
        page.navigate(url("/admin/standings"));
        assertThat(page.locator("h1")).containsText("Tabelle");
    }

    @Test
    void shouldShowImportPage() {
        page.navigate(url("/admin/import"));
        assertThat(page.locator("h1")).containsText("CSV Import");
    }

    @Test
    void shouldShowGeneratePage() {
        page.navigate(url("/admin/generate"));
        assertThat(page.locator("h1")).containsText("Statische Webseite generieren");
        assertThat(page.getByRole(com.microsoft.playwright.options.AriaRole.BUTTON, new com.microsoft.playwright.Page.GetByRoleOptions().setName("Seite generieren"))).isVisible();
    }

    @Test
    void shouldNavigateAllPages() {
        page.navigate(url("/admin/seasons"));

        page.getByRole(com.microsoft.playwright.options.AriaRole.LINK, new com.microsoft.playwright.Page.GetByRoleOptions().setName("Teams")).first().click();
        assertThat(page.locator("h1")).containsText("Teams");

        page.getByRole(com.microsoft.playwright.options.AriaRole.LINK, new com.microsoft.playwright.Page.GetByRoleOptions().setName("Fahrer")).first().click();
        assertThat(page.locator("h1")).containsText("Fahrer");

        page.getByRole(com.microsoft.playwright.options.AriaRole.LINK, new com.microsoft.playwright.Page.GetByRoleOptions().setName("Spieltage")).first().click();
        assertThat(page.locator("h1")).containsText("Spieltage");

        page.getByRole(com.microsoft.playwright.options.AriaRole.LINK, new com.microsoft.playwright.Page.GetByRoleOptions().setName("Rennen")).first().click();
        assertThat(page.locator("h1")).containsText("Rennen");
    }
}
