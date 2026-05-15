package org.ctc.e2e;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.springframework.test.context.jdbc.Sql;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * QUAL-03 regression gate -- proves a season migrated by V4 + cleaned by V6 still opens
 * correctly in the post-migration admin UI. Per , two {@code @Test} methods cover
 * both ROADMAP-SC4 sub-cases (without playoff + with playoff). Both use {@link Sql} to pre-insert
 * the legacy fixture shape into the post-V6 schema; the test then exercises the read-only admin
 * paths only. No write/edit/save UI is invoked.
 *
 * <p><b>Selector reality vs. plan template:</b> The plan referenced placeholder selectors like
 * {@code .phase-tab}, {@code .matchday-row}, {@code .race-row}, {@code .standings-row}. The
 * real templates (two-row tab nav) use {@code .tab-nav .tab-btn} for phase tabs;
 * matchdays render as {@code <ul><li><a>} inside {@code section#matchdays}; standings render
 * inside {@code table#standingsTable tbody tr.data-row}. The fixtures intentionally seed NO
 * race-results, so the standings table is empty and the controller renders the
 * empty-state card instead -- the legacy URL ({@code /admin/standings?seasonId=}) bridge is
 * verified by asserting the page rendered for the seeded season + the REGULAR phase tab is
 * highlighted (/ D-31: server-side phase resolution, not HTTP redirect).
 */
@Tag("e2e")
class LegacyMigratedSeasonE2ETest extends PlaywrightConfig {

	// UUIDs match the deterministic ones in src/test/resources/sql/legacy-season-{with,without}-playoff.sql
	private static final String SEASON_WITHOUT_PLAYOFF_ID = "00000000-0000-0061-0000-000000000010";
	private static final String REGULAR_PHASE_WITHOUT_PLAYOFF_ID = "00000000-0000-0061-0000-000000000011";
	private static final String MATCHDAY_1_WITHOUT_PLAYOFF_ID = "00000000-0000-0061-0000-000000000050";

	private static final String SEASON_WITH_PLAYOFF_ID = "00000000-0000-0061-1000-000000000010";
	private static final String REGULAR_PHASE_WITH_PLAYOFF_ID = "00000000-0000-0061-1000-000000000011";
	private static final String PLAYOFF_PHASE_WITH_PLAYOFF_ID = "00000000-0000-0061-1000-000000000012";
	private static final String PLAYOFF_WITH_PLAYOFF_ID = "00000000-0000-0061-1000-000000000090";
	private static final String MATCHDAY_1_WITH_PLAYOFF_ID = "00000000-0000-0061-1000-000000000050";

	@BeforeEach
	void setUp() {
		setupPage();
	}

	@AfterEach
	void tearDown() {
		teardownPage();
	}

	@Test
	@Sql(scripts = "/sql/legacy-season-without-playoff.sql",
			executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
	void givenLegacyMigratedSeasonWithoutPlayoff_thenRegularTabOnly() {
		// (a) Saison-Detail auto-redirects to REGULAR phase URL (SeasonController D-08).
		page.navigate(url("/admin/seasons/" + SEASON_WITHOUT_PLAYOFF_ID));
		assertThat(page.locator("h1")).containsText("Test-Legacy-Season-2098");

		// (a continued) exactly 1 phase tab visible with REGULAR text, no PLAYOFF tab.
		assertThat(page.locator(".tab-nav .tab-btn:has-text('REGULAR')")).hasCount(1);
		assertThat(page.locator(".tab-nav .tab-btn:has-text('PLAYOFF')")).hasCount(0);

		// (c) Matchday list (2 matchdays per fixture) renders inside section#matchdays.
		assertThat(page.locator("section#matchdays ul li")).hasCount(2);
		assertThat(page.locator("section#matchdays ul li").first()).containsText("Matchday 1");

		// (d) Click the first matchday -> matchday-detail page renders.
		page.locator("section#matchdays ul li a").first().click();
		assertThat(page).hasURL(java.util.regex.Pattern.compile(
				".*/admin/matchdays/" + MATCHDAY_1_WITHOUT_PLAYOFF_ID + "(\\?.*)?$"));
		assertThat(page.locator("h1")).containsText("Matchday 1");

		// (e) The matchday-detail page shows the Matchday header + breadcrumb back-link to
		// /admin/matchdays?seasonId={id}. Both confirm the pure read path renders without 500.
		assertThat(page.locator(".back-link")).isVisible();

		// (f) Legacy-Standings-URL ?seasonId= server-side resolves to REGULAR phase
		// (/ D-31). The controller does NOT redirect; instead the standings page
		// renders with phase != null and the REGULAR tab marked active.
		page.navigate(url("/admin/standings?seasonId=" + SEASON_WITHOUT_PLAYOFF_ID));
		assertThat(page.locator("h1")).containsText("Standings");
		// REGULAR phase tab is rendered with .tab-active (server resolved seasonId -> REGULAR phase).
		assertThat(page.locator(".tab-nav .tab-btn.tab-active")).hasCount(1);
		assertThat(page.locator(".tab-nav .tab-btn.tab-active")).containsText("REGULAR");

		// (g) Standings page either shows the team table or the "no race results yet" empty state
		// (the fixture seeds 0 race-results -- by design, this is a read-only fixture). Both
		// outcomes prove the legacy ?seasonId= bridge resolved the season + phase server-side
		// without 500. The selected-season toolbar paragraph references the season name.
		assertThat(page.locator(".text-dim")).containsText("Test-Legacy-Season-2098");
	}

	@Test
	@Sql(scripts = "/sql/legacy-season-with-playoff.sql",
			executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
	void givenLegacyMigratedSeasonWithPlayoff_thenRegularAndPlayoffTabs() {
		// (a) Saison-Detail auto-redirects to REGULAR phase tab URL.
		page.navigate(url("/admin/seasons/" + SEASON_WITH_PLAYOFF_ID));
		assertThat(page.locator("h1")).containsText("Test-Legacy-Season-2097");

		// (a + b) BOTH a REGULAR tab and a PLAYOFF tab are visible.
		assertThat(page.locator(".tab-nav .tab-btn:has-text('REGULAR')")).hasCount(1);
		assertThat(page.locator(".tab-nav .tab-btn:has-text('PLAYOFF')")).hasCount(1);

		// (c) Matchday list (2 matchdays on the REGULAR phase) renders.
		assertThat(page.locator("section#matchdays ul li")).hasCount(2);

		// (d) Click matchday -> matchday-detail page renders without 500.
		page.locator("section#matchdays ul li a").first().click();
		assertThat(page).hasURL(java.util.regex.Pattern.compile(
				".*/admin/matchdays/" + MATCHDAY_1_WITH_PLAYOFF_ID + "(\\?.*)?$"));
		assertThat(page.locator("h1")).containsText("Matchday 1");

		// (f) Legacy-Standings-URL ?seasonId= resolves server-side to REGULAR phase.
		page.navigate(url("/admin/standings?seasonId=" + SEASON_WITH_PLAYOFF_ID));
		assertThat(page.locator("h1")).containsText("Standings");
		assertThat(page.locator(".tab-nav .tab-btn.tab-active")).containsText("REGULAR");
		assertThat(page.locator(".text-dim")).containsText("Test-Legacy-Season-2097");

		// Switch to PLAYOFF phase tab via canonical ?phase= URL: the legacy season has both
		// phases, the standings tabs let the user navigate between them. Asserts that the
		// PLAYOFF tab in the standings nav links to a working canonical URL.
		page.navigate(url("/admin/standings?phase=" + PLAYOFF_PHASE_WITH_PLAYOFF_ID));
		assertThat(page.locator(".tab-nav .tab-btn.tab-active")).containsText("PLAYOFF");

		// (additional) Click the PLAYOFF tab from the season-detail to land on the PLAYOFF phase
		// page; the bracket section renders with a "View bracket" link to the playoff page
		// (UI-07 / D-08 lean-template). The Playoff page itself loads without 500.
		page.navigate(url("/admin/seasons/" + SEASON_WITH_PLAYOFF_ID
				+ "/phases/" + PLAYOFF_PHASE_WITH_PLAYOFF_ID));
		assertThat(page.locator(".tab-nav .tab-btn.tab-active")).containsText("PLAYOFF");
		assertThat(page.locator("section#bracket")).isVisible();
		// "View bracket" link -> playoff bracket page (loads without 500). The bracket template
		// renders <h1>Playoffs</h1> as the page header (statically); the playoff name lives
		// inside the bracket card's <h2>, which is only emitted when bracket data is non-null.
		// Since the legacy fixture seeds 0 playoff_rounds/matchups, the bracket card is absent
		// -- but the page still rendering proves the legacy migrated playoff is reachable.
		page.locator("section#bracket a:has-text('View bracket')").click();
		assertThat(page).hasURL(java.util.regex.Pattern.compile(
				".*/admin/playoffs/" + PLAYOFF_WITH_PLAYOFF_ID + "(\\?.*)?$"));
		assertThat(page.locator("h1")).containsText("Playoffs");
		// The Saison toolbar dropdown lists the selected season name (proves seasonId resolved).
		assertThat(page.locator("select[name='seasonId'] option[selected]"))
				.containsText("Test-Legacy-Season-2097");
	}
}
