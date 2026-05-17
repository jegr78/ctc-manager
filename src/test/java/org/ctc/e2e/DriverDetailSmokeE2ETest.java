package org.ctc.e2e;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import java.nio.file.Paths;
import java.util.List;
import org.ctc.domain.model.Driver;
import org.ctc.domain.model.Season;
import org.ctc.domain.model.SeasonDriver;
import org.ctc.domain.model.Team;
import org.ctc.domain.repository.DriverRepository;
import org.ctc.domain.repository.SeasonDriverRepository;
import org.ctc.domain.repository.SeasonRepository;
import org.ctc.domain.repository.TeamRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * QUAL-01 visual smoke: drives {@code /admin/drivers/{id}} for a multi-season driver and asserts
 * Season-Assignment chips render in ascending year order, matching the SQL ORDER BY enforced by
 * {@link DriverRepository#findDetailById(java.util.UUID)}.
 */
@Tag("e2e")
class DriverDetailSmokeE2ETest extends PlaywrightConfig {

	@Autowired private DriverRepository driverRepository;
	@Autowired private SeasonRepository seasonRepository;
	@Autowired private TeamRepository teamRepository;
	@Autowired private SeasonDriverRepository seasonDriverRepository;
	@Autowired private TransactionTemplate txTemplate;

	private Driver driver;

	@BeforeEach
	void seedFixture() {
		setupPage();
		driver = txTemplate.execute(tx -> {
			var team = teamRepository.save(new Team("Phase83-Test-DriverDetail-T", "P83D"));
			var s2026 = seasonRepository.save(new Season("Phase83-Test-DriverDetail-2026", 2026, 1));
			var s2024 = seasonRepository.save(new Season("Phase83-Test-DriverDetail-2024", 2024, 1));
			var s2025 = seasonRepository.save(new Season("Phase83-Test-DriverDetail-2025", 2025, 1));
			var d = driverRepository.save(new Driver("P83T_DriverDetail_E2E", "Phase83-Test-DriverDetail-Drv"));
			seasonDriverRepository.save(new SeasonDriver(s2026, d, team));
			seasonDriverRepository.save(new SeasonDriver(s2024, d, team));
			seasonDriverRepository.save(new SeasonDriver(s2025, d, team));
			return d;
		});
	}

	@AfterEach
	void tearDown() {
		teardownPage();
	}

	@Test
	void givenMultiSeasonDriver_whenViewDetailPage_thenSeasonAssignmentChipsRenderInAscendingYearOrder() {
		// given — driver with 2024, 2025, 2026 assignments (inserted out of order)
		page.navigate(url("/admin/drivers/" + driver.getId()));

		// when — chip-list renders
		var chipTexts = page.locator(".chip-list .chip").allTextContents();

		// then — chips appear in ascending year order
		assertThat(page.locator("h1")).containsText("Phase83-Test-DriverDetail-Drv");
		// Each chip contains the season display label (year + number), so a stable substring is the year
		// rendered via Season.displayLabel(). Asserts the order by year.
		assertThatYearOrderAscending(chipTexts);

		// visual evidence
		page.screenshot(new com.microsoft.playwright.Page.ScreenshotOptions()
				.setPath(Paths.get(".screenshots/qual-01-driver-detail-order.png"))
				.setFullPage(true));
	}

	private void assertThatYearOrderAscending(List<String> chipTexts) {
		int[] years = chipTexts.stream()
				.mapToInt(this::extractYear)
				.toArray();
		for (int i = 1; i < years.length; i++) {
			if (years[i] < years[i - 1]) {
				throw new AssertionError("Chips not in ascending year order: " + chipTexts);
			}
		}
	}

	private int extractYear(String chipText) {
		// chipText shape: "<Season.displayLabel> — <Team.shortName>"; displayLabel embeds year as YYYY.
		// Extract the first 4-digit run that's a plausible year (2000-2099).
		var matcher = java.util.regex.Pattern.compile("20\\d{2}").matcher(chipText);
		if (!matcher.find()) {
			throw new AssertionError("No year found in chip text: " + chipText);
		}
		return Integer.parseInt(matcher.group());
	}
}
