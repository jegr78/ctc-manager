package org.ctc.e2e;

import com.microsoft.playwright.options.AriaRole;
import org.ctc.dataimport.GoogleSheetsService;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

@Import(ImportE2eTest.TestGoogleSheetsConfig.class)
class ImportE2eTest extends PlaywrightConfig {

    @TestConfiguration
    static class TestGoogleSheetsConfig {
        @Bean
        @Primary
        GoogleSheetsService googleSheetsService() {
            return new GoogleSheetsService("") {
                @Override
                public boolean isAvailable() {
                    return true;
                }
            };
        }
    }

    @BeforeEach
    void setUp() {
        setupPage();
    }

    @AfterEach
    void tearDown() {
        teardownPage();
    }

    @Test
    void shouldShowImportPageWithNewLayout() {
        page.navigate(url("/admin/import"));

        assertThat(page.locator("h1")).containsText("Import");
        assertThat(page.getByRole(AriaRole.BUTTON,
                new com.microsoft.playwright.Page.GetByRoleOptions().setName("CSV Upload"))).isVisible();
    }

    @Test
    void shouldShowGoogleSheetTabWhenAvailable() {
        page.navigate(url("/admin/import"));

        assertThat(page.getByRole(AriaRole.BUTTON,
                new com.microsoft.playwright.Page.GetByRoleOptions().setName("Google Sheet"))).isVisible();
    }

    @Test
    void shouldShowRegularSeasonFieldsByDefault() {
        page.navigate(url("/admin/import"));

        assertThat(page.locator("#regularFields")).isVisible();
        assertThat(page.locator("#playoffFields")).isHidden();
    }

    @Test
    void shouldToggleToPlayoffFields() {
        page.navigate(url("/admin/import"));

        page.locator("input[name='importType'][value='playoff']").click();

        assertThat(page.locator("#regularFields")).isHidden();
        assertThat(page.locator("#playoffFields")).isVisible();
    }

    @Test
    void shouldToggleBackToRegularFields() {
        page.navigate(url("/admin/import"));

        // Switch to Playoff
        page.locator("input[name='importType'][value='playoff']").click();
        assertThat(page.locator("#playoffFields")).isVisible();

        // Switch back to Regular
        page.locator("input[name='importType'][value='regular']").click();
        assertThat(page.locator("#regularFields")).isVisible();
        assertThat(page.locator("#playoffFields")).isHidden();
    }

    @Test
    void shouldSwitchToGoogleSheetPanel() {
        page.navigate(url("/admin/import"));

        // Click Google Sheet tab
        page.getByRole(AriaRole.BUTTON,
                new com.microsoft.playwright.Page.GetByRoleOptions().setName("Google Sheet")).click();

        // Google Sheet panel visible, CSV panel hidden
        assertThat(page.locator("#panelSheet")).isVisible();
        assertThat(page.locator("#panelCsv")).isHidden();
    }
}
