package org.ctc.e2e;

import com.microsoft.playwright.options.AriaRole;
import org.ctc.dataimport.GoogleSheetsService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

@Import(ImportE2eTest.TestGoogleSheetsConfig.class)
@Tag("e2e")
class ImportE2eTest extends PlaywrightConfig {

	@BeforeEach
	void setUp() {
		setupPage();
	}

	@AfterEach
	void tearDown() {
		teardownPage();
	}

	@Test
	void whenNavigateToImport_thenPageShowsNewLayout() {
		// when
		page.navigate(url("/admin/import"));

		// then
		assertThat(page.locator("h1")).containsText("Import");
		assertThat(page.getByRole(AriaRole.BUTTON,
				new com.microsoft.playwright.Page.GetByRoleOptions().setName("CSV Upload"))).isVisible();
	}

	@Test
	void givenGoogleSheetsAvailable_whenNavigateToImport_thenGoogleSheetTabIsVisible() {
		// given
		// GoogleSheetsService stub returns isAvailable() == true

		// when
		page.navigate(url("/admin/import"));

		// then
		assertThat(page.getByRole(AriaRole.BUTTON,
				new com.microsoft.playwright.Page.GetByRoleOptions().setName("Google Sheet"))).isVisible();
	}

	@Test
	void whenNavigateToImport_thenRegularSeasonFieldsAreShownByDefault() {
		// when
		page.navigate(url("/admin/import"));

		// then
		assertThat(page.locator("#regularFields")).isVisible();
		assertThat(page.locator("#playoffFields")).isHidden();
	}

	@Test
	void givenRegularImportType_whenToggleToPlayoff_thenPlayoffFieldsAreVisible() {
		// given
		page.navigate(url("/admin/import"));

		// when
		page.locator("input[name='importType'][value='playoff']").click();

		// then
		assertThat(page.locator("#regularFields")).isHidden();
		assertThat(page.locator("#playoffFields")).isVisible();
	}

	@Test
	void givenPlayoffSelected_whenToggleBackToRegular_thenRegularFieldsAreVisible() {
		// given
		page.navigate(url("/admin/import"));

		// Switch to Playoff
		page.locator("input[name='importType'][value='playoff']").click();
		assertThat(page.locator("#playoffFields")).isVisible();

		// when
		page.locator("input[name='importType'][value='regular']").click();

		// then
		assertThat(page.locator("#regularFields")).isVisible();
		assertThat(page.locator("#playoffFields")).isHidden();
	}

	@Test
	void givenCsvPanelActive_whenClickGoogleSheetTab_thenGoogleSheetPanelIsVisible() {
		// given
		page.navigate(url("/admin/import"));

		// when
		// Click Google Sheet tab
		page.getByRole(AriaRole.BUTTON,
				new com.microsoft.playwright.Page.GetByRoleOptions().setName("Google Sheet")).click();

		// then
		// Google Sheet panel visible, CSV panel hidden
		assertThat(page.locator("#panelSheet")).isVisible();
		assertThat(page.locator("#panelCsv")).isHidden();
	}

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
}
