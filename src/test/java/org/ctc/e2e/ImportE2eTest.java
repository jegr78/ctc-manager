package org.ctc.e2e;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.options.WaitForSelectorState;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.FilePayload;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.List;
import org.ctc.TestHelper;
import org.ctc.domain.repository.DriverRepository;
import org.springframework.beans.factory.annotation.Autowired;
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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Import(ImportE2eTest.TestGoogleSheetsConfig.class)
@Tag("e2e")
class ImportE2eTest extends PlaywrightConfig {

	@Autowired TestHelper helper;
	@Autowired DriverRepository drivers;
	@Autowired GoogleSheetsService sheets;

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
				new Page.GetByRoleOptions().setName("CSV Upload"))).isVisible();
	}

	@Test
	void givenGoogleSheetsAvailable_whenNavigateToImport_thenGoogleSheetTabIsVisible() {
		// given
		// GoogleSheetsService stub returns isAvailable() == true

		// when
		page.navigate(url("/admin/import"));

		// then
		assertThat(page.getByRole(AriaRole.BUTTON,
				new Page.GetByRoleOptions().setName("Google Sheet"))).isVisible();
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
				new Page.GetByRoleOptions().setName("Google Sheet")).click();

		// then
		// Google Sheet panel visible, CSV panel hidden
		assertThat(page.locator("#panelSheet")).isVisible();
		assertThat(page.locator("#panelCsv")).isHidden();
	}

	@Test
	void givenFuzzyDriver_whenCreateNewIsSelectedInPreview_thenDecisionIsSubmittedAndNewDriverIsCreated() {
		// given
		var suffix = UUID.randomUUID().toString().substring(0, 10);
		var fixture = helper.createFullSeasonFixture("Test Import Preview " + suffix);
		var target = helper.createMatchday(fixture.season(), "Test Empty Import Target", 2);
		var existing = helper.createDriver("Test_" + suffix + "_Fuzzy", "Test Fuzzy " + suffix);
		var incoming = "Test_" + suffix + "_Fuzzi";
		var other = helper.createDriver("Other_" + suffix, "Test Other " + suffix);
		var csv = "Team,PSN ID,Position,Quali,FL\n"
				+ fixture.homeTeam().getShortName() + "," + incoming + ",1,1,true\n"
				+ fixture.awayTeam().getShortName() + "," + other.getPsnId() + ",2,2,false\n";
		var file = new FilePayload("results.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));
		page.navigate(url("/admin/import"));
		page.locator("#seasonId").selectOption(fixture.season().getId().toString());
		page.locator("#matchdayId option[value='" + target.getId() + "']").waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.ATTACHED));
		page.locator("#matchdayId").selectOption(target.getId().toString());
		page.locator("#file").setInputFiles(file);
		page.locator("#csvForm button").click();
		var decision = page.locator("select[name='confirm_" + incoming + "']");
		assertThat(decision).hasValue(existing.getId().toString());

		// when
		decision.selectOption("new");

		// then
		assertEquals("new", page.locator("form[action='/admin/import/execute']")
				.evaluate("form => new FormData(form).get('confirm_" + incoming + "')"));
		assertThat(decision).hasAccessibleName("Driver assignment for " + incoming);

		// when
		page.locator("#file").setInputFiles(file);
		page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Execute Import")).click();

		// then
		assertThat(page.locator(".alert-success")).containsText("Import successful");
		assertTrue(drivers.findByPsnId(incoming).isPresent());
	}

	@Test
	void givenMalformedCsvOnMobile_whenPreviewed_thenErrorsBlockExecutionAndPageFitsViewport() {
		// given
		var fixture = helper.createFullSeasonFixture("Test Blocked Import " + UUID.randomUUID().toString().substring(0, 8));
		page.setViewportSize(390, 768);
		page.navigate(url("/admin/import"));
		page.locator("#seasonId").selectOption(fixture.season().getId().toString());
		page.locator("#matchdayId option[value='" + fixture.matchday().getId() + "']")
				.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.ATTACHED));
		page.locator("#matchdayId").selectOption(fixture.matchday().getId().toString());
		var csv = fixture.homeTeam().getShortName() + ",Test Invalid Position,second,1,false";
		page.locator("#file").setInputFiles(new FilePayload("invalid.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8)));

		// when
		page.locator("#csvForm button").click();

		// then
		assertThat(page.locator("h1")).hasText("Import Preview");
		assertThat(page.locator(".import-preview .alert-error").first()).isVisible();
		assertThat(page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Execute Import"))).isDisabled();
		assertEquals(true,
				page.evaluate("document.documentElement.scrollWidth <= window.innerWidth"));
	}

	@Test
	void givenSameFuzzyDriverInTwoRaces_whenAssignmentChanged_thenBothRacesSubmitTheSameDecision() {
		// given
		var suffix = UUID.randomUUID().toString().substring(0, 8);
		var fixture = helper.createFullSeasonFixture("TestSheet" + suffix);
		var driver = helper.createDriver("TestSheet_" + suffix + "_Fuzzy", "Test Sheet " + suffix);
		var incoming = "TestSheet_" + suffix + "_Fuzzi";
		var stub = (TestSheetsService) sheets;
		stub.rows = List.of(
				List.of(fixture.homeTeam().getShortName(), "Position", "Quali", "FL"),
				List.of(incoming, 1, 1, false), List.of("Overall", "", "", ""));
		page.navigate(url("/admin/import"));
		page.locator("#seasonId").selectOption(fixture.season().getId().toString());
		page.locator("#matchdayId option[value='" + fixture.matchday().getId() + "']")
				.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.ATTACHED));
		page.locator("#matchdayId").selectOption(fixture.matchday().getId().toString());
		page.locator("#tabSheet").click();
		page.locator("#sheetUrl").fill("https://docs.google.com/spreadsheets/d/test-import-preview");
		page.locator("#sheetForm button").click();
		var decisions = page.locator("select[name='confirm_" + incoming + "']");
		assertThat(decisions).hasCount(2);
		assertThat(decisions.first()).hasValue(driver.getId().toString());

		// when
		decisions.last().selectOption("new");

		// then
		assertThat(decisions.first()).hasValue("new");
		assertThat(decisions.last()).hasAccessibleName("Driver assignment for " + incoming);
		assertEquals(List.of("new", "new"), page.locator("#import-execute-form")
				.evaluate("form => new FormData(form).getAll('confirm_" + incoming + "')"));
		assertEquals(true, decisions.evaluateAll("els => new Set(els.map(el => el.id)).size === els.length"));
	}

	@Test
	void givenSharedImportFields_whenSourceAndTypeChange_thenOnlyActiveTargetBelongsToSelectedForm() {
		// given
		var fixture = helper.createFullSeasonFixture("Test Import Source " + UUID.randomUUID().toString().substring(0, 8));
		page.navigate(url("/admin/import"));
		page.locator("#file").setInputFiles(new FilePayload("source.csv", "text/csv", "test".getBytes(StandardCharsets.UTF_8)));
		assertEquals(false, page.locator("#csvForm").evaluate("form => form.checkValidity()"));
		page.locator("#seasonId").selectOption(fixture.season().getId().toString());
		page.locator("#matchdayId option[value='" + fixture.matchday().getId() + "']")
				.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.ATTACHED));
		page.locator("#matchdayId").selectOption(fixture.matchday().getId().toString());

		// when
		page.locator("#tabSheet").click();

		// then
		assertThat(page.locator("#tabSheet")).hasAttribute("aria-pressed", "true");
		assertThat(page.locator("#seasonId")).hasAttribute("form", "sheetForm");
		assertEquals(fixture.matchday().getId().toString(), page.locator("#sheetForm")
				.evaluate("form => new FormData(form).get('matchdayId')"));
		assertEquals(null, page.locator("#sheetForm").evaluate("form => new FormData(form).get('playoffMatchupId')"));

		// when
		page.locator("input[name='importType'][value='playoff']").check();

		// then
		assertThat(page.locator("#matchdayId")).isDisabled();
		assertThat(page.locator("#playoffMatchupId")).isEnabled();
		assertEquals(null, page.locator("#sheetForm").evaluate("form => new FormData(form).get('matchdayId')"));

		// when
		page.locator("#tabCsv").click();
		page.locator("input[name='importType'][value='regular']").check();

		// then
		assertThat(page.locator("#matchdayId")).hasValue(fixture.matchday().getId().toString());
		assertEquals(true, page.locator("#csvForm").evaluate("form => form.checkValidity()"));
		assertEquals("source.csv", page.locator("#file").evaluate("el => el.files[0].name"));
	}

	@Test
	void givenDelayedMatchdayResponse_whenSeasonChanges_thenOnlyCurrentSeasonTargetsRemain() {
		// given
		var suffix = UUID.randomUUID().toString().substring(0, 8);
		var first = helper.createFullSeasonFixture("Test Slow Import " + suffix);
		var second = helper.createFullSeasonFixture("Test Current Import " + suffix);
		page.addInitScript("""
				const originalFetch = window.fetch;
				let delayFirst = true;
				window.fetch = async (...args) => {
				    const response = await originalFetch(...args);
				    if (String(args[0]).includes('/admin/matchdays/by-season') && delayFirst) {
				        delayFirst = false;
				        const data = await response.json();
				        response.json = () => new Promise(resolve => {
				            window.releaseOldMatchdays = () => { resolve(data); window.oldMatchdaysReleased = true; };
				        });
				    }
				    return response;
				};
				""");
		page.navigate(url("/admin/import"));
		page.locator("#seasonId").selectOption(first.season().getId().toString());
		page.waitForFunction("typeof window.releaseOldMatchdays === 'function'");

		// when
		page.locator("#seasonId").selectOption(second.season().getId().toString());
		page.locator("#matchdayId option[value='" + second.matchday().getId() + "']")
				.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.ATTACHED));
		page.evaluate("window.releaseOldMatchdays()");
		page.waitForFunction("window.oldMatchdaysReleased === true");

		// then
		assertThat(page.locator("#matchdayId option[value='" + first.matchday().getId() + "']")).hasCount(0);
		assertThat(page.locator("#matchdayId option[value='" + second.matchday().getId() + "']")).hasCount(1);
	}

	@Test
	void givenSeasonWithoutMatchdays_whenLabelIsCorrectedAndCreated_thenNewTargetIsSelectedForPreview() {
		// given
		var season = helper.createSeason("Test Inline Import " + UUID.randomUUID().toString().substring(0, 8));
		page.navigate(url("/admin/import"));
		page.locator("#seasonId").selectOption(season.getId().toString());
		assertThat(page.locator("#newMatchdayPanel")).isVisible();

		// when
		page.locator("#newMatchdayCreate").click();

		// then
		assertThat(page.locator("#newMatchdayError")).containsText("Label must not be empty");
		assertThat(page.locator("#newMatchdayLabel")).hasAttribute("aria-invalid", "true");

		// when
		page.locator("#newMatchdayLabel").fill("Test New Import Matchday");
		page.locator("#newMatchdayLabel").press("Enter");

		// then
		assertThat(page.locator("#newMatchdayPanel")).isHidden();
		assertThat(page.locator("#matchdayId")).isEnabled();
		assertThat(page.locator("#matchdayId option:checked")).hasText("Test New Import Matchday");
		assertThat(page.locator("#import-target-status")).containsText("created and selected");
		assertEquals(page.locator("#matchdayId").inputValue(), page.locator("#csvForm")
				.evaluate("form => new FormData(form).get('matchdayId')"));
	}

	@TestConfiguration
	static class TestGoogleSheetsConfig {
		@Bean
		@Primary
		GoogleSheetsService googleSheetsService() {
			return new TestSheetsService();
		}
	}

	static class TestSheetsService extends GoogleSheetsService {
		List<List<Object>> rows = List.of();
		TestSheetsService() { super(""); }
		@Override public boolean isAvailable() { return true; }
		@Override public List<String> getSheetNames(String spreadsheetId) { return List.of("Race 1", "Race 2"); }
		@Override public List<List<Object>> readRangeFromSheet(String spreadsheetId, String sheetName, String range) { return rows; }
	}
}
