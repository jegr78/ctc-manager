package org.ctc.e2e;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

@Tag("e2e")
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
	void whenNavigateToRaceScoringPage_thenScoringNavigationLinksAreVisible() {
		// when
		page.navigate(url("/admin/race-scorings"));

		// then
		assertThat(page.getByRole(com.microsoft.playwright.options.AriaRole.LINK,
				new com.microsoft.playwright.Page.GetByRoleOptions().setName("Race-Scorings").setExact(true))).isVisible();
		assertThat(page.getByRole(com.microsoft.playwright.options.AriaRole.LINK,
				new com.microsoft.playwright.Page.GetByRoleOptions().setName("Match-Scorings").setExact(true))).isVisible();
	}

	@Test
	void whenNavigateToRaceScorings_thenListIncludesCtcStandard() {
		// when
		page.navigate(url("/admin/race-scorings"));

		// then
		assertThat(page).hasTitle("CTC Admin - Race-Scorings");
		assertThat(page.locator("h1")).containsText("Race-Scorings");
		// DevDataSeeder creates "CTC Standard"
		assertThat(page.locator("table")).containsText("CTC Standard");
	}

	@Test
	void givenRaceScoringForm_whenSaveWithValidData_thenScoringAppearsInList() {
		// given
		page.navigate(url("/admin/race-scorings/new"));
		assertThat(page.locator("h1")).containsText("New Race-Scoring");

		// when
		page.fill("#name", "E2E Test Scoring");
		// Add 3 race point positions
		page.click("text=+ Add");
		page.click("text=+ Add");
		page.click("text=+ Add");

		// Set values in race points inputs
		var raceInputs = page.locator("#racePointsRows input[type=number]");
		raceInputs.nth(0).fill("15");
		raceInputs.nth(1).fill("10");
		raceInputs.nth(2).fill("5");

		page.fill("#fastestLapPoints", "1");
		page.click("text=Save");

		// then
		assertThat(page.locator(".alert-success")).containsText("Race-Scoring saved");
		assertThat(page.locator("table")).containsText("E2E Test Scoring");
	}

	@Test
	void whenNavigateToMatchScorings_thenListIncludesStandard310() {
		// when
		page.navigate(url("/admin/match-scorings"));

		// then
		assertThat(page).hasTitle("CTC Admin - Match-Scorings");
		assertThat(page.locator("h1")).containsText("Match-Scorings");
		assertThat(page.locator("table")).containsText("Standard 3-1-0");
	}

	@Test
	void givenMatchScoringForm_whenSaveWithValidData_thenScoringAppearsInList() {
		// given
		page.navigate(url("/admin/match-scorings/new"));
		assertThat(page.locator("h1")).containsText("New Match-Scoring");

		// when
		page.fill("#name", "E2E Match 2-1-0");
		page.fill("#pointsWin", "2");
		page.fill("#pointsDraw", "1");
		page.fill("#pointsLoss", "0");
		page.click("text=Save");

		// then
		assertThat(page.locator(".alert-success")).containsText("Match-Scoring saved");
		assertThat(page.locator("table")).containsText("E2E Match 2-1-0");
	}

	@Test
	void givenNonMonotonicPointValues_whenSaveRaceScoring_thenValidationErrorIsShown() {
		// given
		page.navigate(url("/admin/race-scorings/new"));
		page.fill("#name", "Invalid Scoring");

		// when
		// Add positions with non-monotonic values: 10, 20, 5
		page.click("text=+ Add");
		page.click("text=+ Add");
		page.click("text=+ Add");

		var raceInputs = page.locator("#racePointsRows input[type=number]");
		raceInputs.nth(0).fill("10");
		raceInputs.nth(1).fill("20");
		raceInputs.nth(2).fill("5");

		page.fill("#fastestLapPoints", "0");
		page.click("text=Save");

		// then
		assertThat(page.locator(".alert-error")).containsText("monotonically decreasing");
	}
}
