package org.ctc.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.ViewportSize;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;
import org.ctc.TestHelper;
import org.ctc.TestHelper.SeasonFixture;
import org.ctc.domain.model.Driver;
import org.ctc.domain.repository.DriverRepository;
import org.ctc.domain.repository.TeamRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

@Tag("e2e")
class RaceResultsMobileE2ETest extends PlaywrightConfig {

	@Autowired private TestHelper testHelper;
	@Autowired private TeamRepository teamRepository;
	@Autowired private DriverRepository driverRepository;
	@Autowired private TransactionTemplate txTemplate;

	private final String id = UUID.randomUUID().toString().substring(0, 8);
	private SeasonFixture fixture;
	private Driver driver;

	@BeforeEach
	void seedRaceWithADriver() {
		txTemplate.executeWithoutResult(tx -> {
			fixture = testHelper.createFullSeasonFixture("Test_ResultsMobile_" + id);
			driver = testHelper.createDriver("Test_ResultsMobile_" + id, "Test Results Mobile Driver");
			testHelper.createSeasonDriver(fixture.season(), driver, fixture.homeTeam());
		});
	}

	@AfterEach
	void removeFixture() {
		testHelper.deleteSeasonCascade(fixture.season());
		teamRepository.deleteAll(List.of(fixture.homeTeam(), fixture.awayTeam()));
		driverRepository.delete(driver);
	}

	@Test
	void givenPhoneViewport_whenResultsFormOpened_thenOnlyTheTableScrollsSideways() {
		try (BrowserContext phone = browser.newContext(
				new Browser.NewContextOptions().setViewportSize(new ViewportSize(390, 844)))) {
			Page phonePage = phone.newPage();

			// when
			phonePage.navigate(url("/admin/races/" + fixture.race().getId() + "/results"));

			// then
			int pageWidth = ((Number) phonePage.evaluate("document.documentElement.scrollWidth")).intValue();
			int viewportWidth = ((Number) phonePage.evaluate("document.documentElement.clientWidth")).intValue();
			assertThat(pageWidth).as("page width on a %d px phone", viewportWidth).isEqualTo(viewportWidth);
			int tableWidth = ((Number) phonePage.evaluate("document.querySelector('.table-scroll').scrollWidth")).intValue();
			int tableBox = ((Number) phonePage.evaluate("document.querySelector('.table-scroll').clientWidth")).intValue();
			assertThat(tableWidth).as("results table scrolling inside its card").isGreaterThan(tableBox);
			phonePage.screenshot(new Page.ScreenshotOptions()
					.setPath(Paths.get(".screenshots/race-results-mobile.png")).setFullPage(true));
		}
	}
}
