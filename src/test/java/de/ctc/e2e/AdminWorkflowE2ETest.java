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
    }

    @Test
    void shouldCreateSeasonAndShowInList() {
        page.navigate(url("/admin/seasons/new"));
        page.getByRole(com.microsoft.playwright.options.AriaRole.TEXTBOX, new com.microsoft.playwright.Page.GetByRoleOptions().setName("Name").setExact(true)).fill("E2E Season");
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
        assertThat(page.getByRole(com.microsoft.playwright.options.AriaRole.CELL, new com.microsoft.playwright.Page.GetByRoleOptions().setName("e2e_driver").setExact(true))).isVisible();
        assertThat(page.locator(".alert-success")).containsText("Driver saved");
    }

    @Test
    void shouldShowEmptyStandings() {
        page.navigate(url("/admin/standings"));
        assertThat(page.locator("h1")).containsText("Standings");
    }

    @Test
    void shouldShowImportPage() {
        page.navigate(url("/admin/import"));
        assertThat(page.locator("h1")).containsText("CSV Import");
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
    }
}
