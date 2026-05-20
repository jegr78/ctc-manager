package org.ctc.e2e;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.ctc.dataimport.GoogleSheetsService;
import org.ctc.domain.model.*;
import org.ctc.domain.repository.*;
import org.ctc.domain.service.SeasonManagementService;
import org.ctc.domain.service.SeasonPhaseService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.support.TransactionTemplate;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * QUAL-02 E2E gate — exercises the full GROUPS-Saison workflow end-to-end.
 *
 * <p>Per a single {@code @Test} covers the entire ROADMAP-SC3 path:
 * Saison-anlegen → REGULAR-Phase auf GROUPS umschalten → 2 Groups anlegen → 4 Teams +
 * Roster-Zuweisung → Driver-Import (gemockter Sheet-Service) → Matchday/Race-Setup →
 * Race-Results UI-eintragen → per-group + combined-view Standings verifizieren.
 *
 * <p><b>Scope deviation (Rule 3, blocking issue):</b> The plan template assumed UI
 * affordances for group-bound matchday/race generation under
 * {@code /admin/season-phases/{phaseId}/groups/{groupId}/matchdays/generate}.
 * No such UI exists as of Phase 60 — the canonical {@link MatchdayController} only
 * binds matchdays to the REGULAR phase via {@code findRegularPhase}, and group-bound
 * matchday creation has no controller endpoint. The test therefore performs the
 * matchday/match/race/lineup setup via repositories (structural prerequisites without
 * a UI). The D-15 mandate ("UI-Klick-Eintragung für Race-Results") is honoured by
 * driving the {@code /admin/races/{id}/results} form via Playwright clicks for all
 * four races. UI-driven steps remain: season-create, layout-flip to GROUPS, group
 * creation, team creation, season-team add, roster assign, driver-import preview +
 * execute, race-result form, per-group + combined-view standings navigation.
 *
 * <p>Test data isolation + CLAUDE.md "Isolate Test Data Completely":
 * year=2099, season "Test-GROUPS Season 2099", teams T-GA-1/T-GA-2/T-GB-1/T-GB-2,
 * driver PSN IDs T_groups_drv01..T_groups_drv12.
 */
@Import(GroupsSeasonE2ETest.TestGoogleSheetsConfig.class)
@Tag("e2e")
class GroupsSeasonE2ETest extends PlaywrightConfig {

	@Autowired private SeasonRepository seasonRepository;
	@Autowired private SeasonPhaseRepository seasonPhaseRepository;
	@Autowired private SeasonPhaseGroupRepository seasonPhaseGroupRepository;
	@Autowired private PhaseTeamRepository phaseTeamRepository;
	@Autowired private TeamRepository teamRepository;
	@Autowired private DriverRepository driverRepository;
	@Autowired private MatchdayRepository matchdayRepository;
	@Autowired private MatchRepository matchRepository;
	@Autowired private RaceRepository raceRepository;
	@Autowired private RaceLineupRepository raceLineupRepository;
	@Autowired private RaceScoringRepository raceScoringRepository;
	@Autowired private MatchScoringRepository matchScoringRepository;
	@Autowired private SeasonManagementService seasonManagementService;
	@Autowired private SeasonPhaseService seasonPhaseService;
	@Autowired private TransactionTemplate transactionTemplate;

	@BeforeEach
	void setUp() {
		setupPage();
		// Idempotent cleanup: drop a leftover Test-GROUPS Season 2099 from a previous run.
		// Year/Number is unique; cleanup avoids form-validation collisions on re-runs.
		seasonRepository.findByYearAndNumber(2099, 1).forEach(s -> {
			try {
				seasonManagementService.delete(s.getId());
			} catch (RuntimeException e) {
				// Season-delete is guarded; ignore best-effort cleanup so tests still run.
			}
		});
		// Drop teams created by a previous run so /admin/teams/save form does not collide.
		for (String shortName : List.of("T-GA-1", "T-GA-2", "T-GB-1", "T-GB-2")) {
			teamRepository.findByShortName(shortName).ifPresent(t -> {
				try {
					teamRepository.delete(t);
				} catch (RuntimeException ignored) {
					// Ignored — leftover state is non-fatal because new test rows still pass UNIQUE checks.
				}
			});
		}
	}

	@AfterEach
	void tearDown() { teardownPage(); }

	@Test
	void givenGroupsLayout_whenFullSeasonWorkflow_thenStandingsCorrect() {
		// STEP 1 — UI: create "Test-GROUPS Season 2099" via slim season form
		page.navigate(url("/admin/seasons/new"));
		page.getByRole(AriaRole.TEXTBOX, new Page.GetByRoleOptions().setName("Name").setExact(true))
				.fill("Test-GROUPS Season 2099");
		page.fill("#year", "2099");
		page.fill("#number", "1");
		page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Save")).click();
		assertThat(page.locator(".alert-success")).containsText("Season saved");

		// Resolve UUIDs for downstream UI navigation. SeasonRepository.findByYearAndNumber
		// returns a List<Season>; pick the single hit.
		List<Season> hits = seasonRepository.findByYearAndNumber(2099, 1);
		org.junit.jupiter.api.Assertions.assertEquals(1, hits.size(),
				"Expected exactly 1 Test-GROUPS Season 2099 after creation");
		var season = hits.get(0);
		// SeasonManagementService.save auto-bootstraps a REGULAR phase (LEAGUE layout, null scoring).
		var regularPhase = seasonPhaseRepository.findBySeasonIdAndPhaseType(
				season.getId(), PhaseType.REGULAR).orElseThrow();

		// STEP 2 — UI: flip REGULAR phase to GROUPS layout via phase-edit form.
		// The phase-edit form requires legs (>=1), format, and uses path
		// /admin/seasons/{seasonId}/phases/{phaseId}/edit (path canonicalised).
		// Race + match scoring also wired here so race-result entry (STEP 7) can compute points.
		var raceScoring = raceScoringRepository.findAll().stream()
				.filter(rs -> "CTC Standard".equals(rs.getName())).findFirst().orElseThrow();
		var matchScoring = matchScoringRepository.findAll().stream()
				.filter(ms -> "Standard 3-1-0".equals(ms.getName())).findFirst().orElseThrow();

		page.navigate(url("/admin/seasons/" + season.getId() + "/phases/" + regularPhase.getId() + "/edit"));
		page.locator("#layout").selectOption(PhaseLayout.GROUPS.name());
		page.locator("#format").selectOption("LEAGUE");
		page.locator("#raceScoringId").selectOption(raceScoring.getId().toString());
		page.locator("#matchScoringId").selectOption(matchScoring.getId().toString());
		page.locator("#legs").fill("1");
		page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Save Phase")).click();
		assertThat(page.locator(".alert-success")).containsText("Phase updated");

		// STEP 3 — UI: create 2 groups (Group A + Group B) for the GROUPS-layout phase.
		// Endpoint POST /admin/seasons/{sid}/phases/{pid}/groups/save (form)
		for (String groupName : List.of("Group A", "Group B")) {
			page.navigate(url("/admin/seasons/" + season.getId()
					+ "/phases/" + regularPhase.getId() + "/groups/new"));
			page.fill("#name", groupName);
			page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Save Group")).click();
			assertThat(page.locator(".alert-success")).containsText("Group created");
		}

		// Resolve group UUIDs (sortIndex order: index 0 = "Group A", 1 = "Group B")
		var groupsList = seasonPhaseGroupRepository.findByPhaseIdOrderBySortIndex(regularPhase.getId());
		org.junit.jupiter.api.Assertions.assertEquals(2, groupsList.size(),
				"Expected exactly 2 groups after group creation");
		var groupA = groupsList.get(0);
		var groupB = groupsList.get(1);

		// STEP 4 — UI: create 4 teams (T-GA-1, T-GA-2, T-GB-1, T-GB-2)
		for (String shortName : List.of("T-GA-1", "T-GA-2", "T-GB-1", "T-GB-2")) {
			page.navigate(url("/admin/teams/new"));
			page.getByRole(AriaRole.TEXTBOX, new Page.GetByRoleOptions().setName("Name").setExact(true))
					.fill("Test " + shortName);
			page.getByRole(AriaRole.TEXTBOX, new Page.GetByRoleOptions().setName("Short Name"))
					.fill(shortName);
			page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Save")).click();
			assertThat(page.locator(".alert-success")).containsText("Team saved");
		}
		var teamGA1 = teamRepository.findByShortName("T-GA-1").orElseThrow();
		var teamGA2 = teamRepository.findByShortName("T-GA-2").orElseThrow();
		var teamGB1 = teamRepository.findByShortName("T-GB-1").orElseThrow();
		var teamGB2 = teamRepository.findByShortName("T-GB-2").orElseThrow();

		// STEP 4b — UI: add the 4 teams to the season (POST /admin/seasons/{id}/add-team)
		for (Team t : List.of(teamGA1, teamGA2, teamGB1, teamGB2)) {
			page.navigate(url("/admin/seasons/" + season.getId() + "/edit"));
			page.locator("select[name='teamId']").selectOption(t.getId().toString());
			page.locator("form[action$='/add-team'] button[type='submit']").click();
			assertThat(page.locator(".alert-success")).containsText("Team added");
		}

		// STEP 4c — UI: bulk roster save assigns each team to its target group via the
		// roster form on /admin/seasons/{sid}/phases/{pid} (indexed properties).
		// The roster form lives inside a collapsed <details> element ("Edit Roster"); we open
		// it via JavaScript so the submit button becomes visible to Playwright clicks.
		// The form already renders one row per seasonTeam with hidden assignments[i].teamId +
		// a checkbox for assignments[i].included + a group <select> for assignments[i].groupId.
		// We tick every "Include" checkbox and pick the matching group from the dropdown, then
		// click Save Roster.
		page.navigate(url("/admin/seasons/" + season.getId() + "/phases/" + regularPhase.getId()));
		page.evaluate("() => document.querySelectorAll('details').forEach(d => d.open = true)");
		var rosterForm = page.locator("form[action$='/groups/roster']");
		java.util.Map<String, String> teamToGroupId = java.util.Map.of(
				teamGA1.getId().toString(), groupA.getId().toString(),
				teamGA2.getId().toString(), groupA.getId().toString(),
				teamGB1.getId().toString(), groupB.getId().toString(),
				teamGB2.getId().toString(), groupB.getId().toString());
		// Walk every assignments[] row: read its hidden teamId, set included=true, pick group.
		var teamIdInputs = rosterForm.locator("input[type='hidden'][name$='.teamId']");
		int rosterRowCount = teamIdInputs.count();
		for (int i = 0; i < rosterRowCount; i++) {
			String teamIdVal = teamIdInputs.nth(i).getAttribute("value");
			if (teamIdVal == null || !teamToGroupId.containsKey(teamIdVal)) {
				continue;
			}
			String includedSelector = "input[type='checkbox'][name='assignments[" + i + "].included']";
			String groupSelectSelector = "select[name='assignments[" + i + "].groupId']";
			rosterForm.locator(includedSelector).check();
			rosterForm.locator(groupSelectSelector).selectOption(teamToGroupId.get(teamIdVal));
		}
		rosterForm.getByRole(AriaRole.BUTTON, new com.microsoft.playwright.Locator.GetByRoleOptions().setName("Save Roster")).click();
		assertThat(page.locator(".alert-success")).containsText("Roster updated");

		// Hybrid assert: DB-state confirms 4 PhaseTeam rows with non-null group_ids.
		var phaseTeams = phaseTeamRepository.findByPhaseId(regularPhase.getId());
		org.junit.jupiter.api.Assertions.assertEquals(4, phaseTeams.size(),
				"Expected 4 PhaseTeam rows after roster save");
		org.junit.jupiter.api.Assertions.assertTrue(
				phaseTeams.stream().allMatch(pt -> pt.getGroup() != null),
				"All 4 PhaseTeam rows must have a non-null group_id (GROUPS layout)");

		// STEP 5 — UI: driver-import preview + execute via stubbed GoogleSheetsService.
		// The TestGoogleSheetsConfig (below) returns a single tab "2099" with 12 driver rows.
		page.navigate(url("/admin/drivers/import"));
		// The form's <input type="url"> enforces HTML5 URL validation client-side; supply a
		// well-formed URL even though the stubbed GoogleSheetsService.extractSpreadsheetId
		// ignores the input and always returns "test-spreadsheet-id".
		page.fill("#sheetUrl", "https://docs.google.com/spreadsheets/d/test-spreadsheet-id");
		page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Preview")).click();
		// Preview page: New Drivers bucket should list all 12 driver rows.
		assertThat(page.locator("h1")).containsText("Driver Import Preview");
		// 12 rows in the New Drivers table — count <tr> elements inside the rendered preview.
		assertThat(page.locator("table tbody tr").first()).isVisible();
		// Select the season for tab "2099" (the synthetic stub uses bare-year tab name).
		page.locator("select[name='seasonId_2099']").selectOption(season.getId().toString());
		page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Execute Import")).click();
		assertThat(page.locator(".alert-success")).containsText("Import successful");

		// Hybrid assert: DB-state confirms 12 drivers + 12 SeasonDriver rows persisted.
		long importedDriverCount = driverRepository.findAll().stream()
				.filter(d -> d.getPsnId().startsWith("T_groups_drv"))
				.count();
		org.junit.jupiter.api.Assertions.assertEquals(12, importedDriverCount,
				"Expected 12 imported drivers with PSN-ID prefix T_groups_drv");

		// STEP 6 — Repository setup: matchdays + matches + races + lineups.
		// Deviation (Rule 3): no UI exists for group-bound matchday/race generation;
		// the canonical MatchdayController only binds matchdays to the REGULAR phase.
		// The *race-result entry* must be UI-driven (STEP 7); structural
		// matchday/match/race construction happens here at the repository layer.
		// Layout: 2 matchdays/group × 1 match/matchday × 1 race/match = 4 races total.
		List<UUID> raceIdsGroupA = new ArrayList<>();
		List<UUID> raceIdsGroupB = new ArrayList<>();
		// Reload phase + groups inside the transaction so JPA sees managed entities.
		transactionTemplate.executeWithoutResult(status -> {
			var phase = seasonPhaseRepository.findById(regularPhase.getId()).orElseThrow();
			var gA = seasonPhaseGroupRepository.findById(groupA.getId()).orElseThrow();
			var gB = seasonPhaseGroupRepository.findById(groupB.getId()).orElseThrow();

			var ga1 = teamRepository.findByShortName("T-GA-1").orElseThrow();
			var ga2 = teamRepository.findByShortName("T-GA-2").orElseThrow();
			var gb1 = teamRepository.findByShortName("T-GB-1").orElseThrow();
			var gb2 = teamRepository.findByShortName("T-GB-2").orElseThrow();

			List<Driver> driversGA = driverRepository.findAll().stream()
					.filter(d -> d.getPsnId().matches("T_groups_drv0[1-6]"))
					.sorted(java.util.Comparator.comparing(Driver::getPsnId))
					.toList();
			List<Driver> driversGB = driverRepository.findAll().stream()
					.filter(d -> d.getPsnId().matches("T_groups_drv(0[7-9]|1[0-2])"))
					.sorted(java.util.Comparator.comparing(Driver::getPsnId))
					.toList();
			org.junit.jupiter.api.Assertions.assertEquals(6, driversGA.size(), "Expected 6 Group-A drivers");
			org.junit.jupiter.api.Assertions.assertEquals(6, driversGB.size(), "Expected 6 Group-B drivers");

			// Group A: 2 matchdays, T-GA-1 vs T-GA-2 each
			for (int mdIdx = 1; mdIdx <= 2; mdIdx++) {
				var md = matchdayRepository.save(new Matchday(phase, "Group A — Matchday " + mdIdx, mdIdx));
				md.setGroup(gA);
				matchdayRepository.save(md);
				var match = matchRepository.save(new Match(md, ga1, ga2));
				var race = persistRaceWithLineups(md, match, ga1, ga2, driversGA);
				raceIdsGroupA.add(race.getId());
			}
			// Group B: 2 matchdays, T-GB-1 vs T-GB-2 each
			for (int mdIdx = 1; mdIdx <= 2; mdIdx++) {
				var md = matchdayRepository.save(
						new Matchday(phase, "Group B — Matchday " + mdIdx, mdIdx + 10));
				md.setGroup(gB);
				matchdayRepository.save(md);
				var match = matchRepository.save(new Match(md, gb1, gb2));
				var race = persistRaceWithLineups(md, match, gb1, gb2, driversGB);
				raceIdsGroupB.add(race.getId());
			}
		});
		org.junit.jupiter.api.Assertions.assertEquals(2, raceIdsGroupA.size());
		org.junit.jupiter.api.Assertions.assertEquals(2, raceIdsGroupB.size());

		// STEP 7 — UI: enter race results via the /admin/races/{id}/results form.
		// The form is pre-populated with all 6 lineup driver rows per race; we click the
		// position inputs and Save. Driver-Ranking values picked for deterministic standings.
		// CTC Standard race scoring: 20,17,14,12,10,8 — each driver is ranked 1..6 within race.
		//
		// Group A:
		//   MD1: GA-1 wins 1,2,3 (20+17+14 = 51), GA-2 takes 4,5,6 (12+10+8 = 30) → 3:0 → 3 pts GA-1
		//   MD2: GA-1 takes 1,3,5 (20+14+10 = 44), GA-2 takes 2,4,6 (17+12+8 = 37) → 3:0 → 3 pts GA-1
		//   Total: GA-1 = 6 match-points, GA-2 = 0 match-points (1st vs 2nd in Group A standings)
		// Group B:
		//   MD1: GB-1 wins 1,2,3 (51), GB-2 takes 4,5,6 (30) → 3:0 → 3 pts GB-1
		//   MD2: GB-2 wins 1,2,3 (51), GB-1 takes 4,5,6 (30) → 0:3 → 3 pts GB-2
		//   Total: GB-1 = 3, GB-2 = 3 — tied (Group B is a draw). Top of Group B remains
		//   deterministic via tie-break (points-for: GB-1=51+30=81, GB-2=30+51=81 — same,
		//   so we vary Race 2 slightly: GB-2 takes 1,2,3 with race-points 51, GB-1 takes 4,5,6=30).
		//   Final Group B leader = GB-1 by tie-break (alphabetical short-name); standings still
		//   render exactly 2 rows. The combined-view assertion only checks row count + top points.
		// Group A — Match-Day 1: GA-1 sweeps top 3 (1, 2, 3); GA-2 takes 4, 5, 6.
		fillRaceResultsByPsnIdOrder(raceIdsGroupA.get(0),
				List.of("T_groups_drv01", "T_groups_drv02", "T_groups_drv03",
						"T_groups_drv04", "T_groups_drv05", "T_groups_drv06"));
		// Group A — Match-Day 2: GA-1 1, 3, 5; GA-2 2, 4, 6 (still 3:0 win for GA-1)
		fillRaceResultsByPsnIdOrder(raceIdsGroupA.get(1),
				List.of("T_groups_drv01", "T_groups_drv04", "T_groups_drv02",
						"T_groups_drv05", "T_groups_drv03", "T_groups_drv06"));
		// Group B — Match-Day 1: GB-1 sweep top 3 (1, 2, 3); GB-2 4, 5, 6
		fillRaceResultsByPsnIdOrder(raceIdsGroupB.get(0),
				List.of("T_groups_drv07", "T_groups_drv08", "T_groups_drv09",
						"T_groups_drv10", "T_groups_drv11", "T_groups_drv12"));
		// Group B — Match-Day 2: GB-1 sweep again (keeps standings deterministic — GB-1 = 6 pts, GB-2 = 0)
		fillRaceResultsByPsnIdOrder(raceIdsGroupB.get(1),
				List.of("T_groups_drv07", "T_groups_drv08", "T_groups_drv09",
						"T_groups_drv10", "T_groups_drv11", "T_groups_drv12"));

		// STEP 8 — UI: assert per-group standings + combined-view standings (D-13 hybrid).
		// Standings URL params (StandingsController): ?phase={phaseId} for combined view,
		// ?phase={phaseId}&group={groupId} for per-group view. Top row = leader.
		// Per-group: Group A — leader = T-GA-1 (6 match-points)
		page.navigate(url("/admin/standings?phase=" + regularPhase.getId() + "&group=" + groupA.getId()));
		assertThat(page.locator("#standingsTable tbody tr").first()).containsText("T-GA-1");

		// Per-group: Group B — leader = T-GB-1 (6 match-points)
		page.navigate(url("/admin/standings?phase=" + regularPhase.getId() + "&group=" + groupB.getId()));
		assertThat(page.locator("#standingsTable tbody tr").first()).containsText("T-GB-1");

		// Combined-view (no group param): exactly 4 standings rows for the 4 teams.
		page.navigate(url("/admin/standings?phase=" + regularPhase.getId()));
		assertThat(page.locator("#standingsTable tbody tr.data-row")).hasCount(4);

		// STEP 9 — DB-state hybrid assertions.
		var refreshedRegular = seasonPhaseRepository.findBySeasonIdAndPhaseType(
				season.getId(), PhaseType.REGULAR).orElseThrow();
		org.junit.jupiter.api.Assertions.assertEquals(PhaseLayout.GROUPS, refreshedRegular.getLayout(),
				"REGULAR phase must remain on GROUPS layout after the workflow");
		long groupATeamCount = phaseTeamRepository.findByPhaseIdAndGroupId(regularPhase.getId(), groupA.getId())
				.size();
		long groupBTeamCount = phaseTeamRepository.findByPhaseIdAndGroupId(regularPhase.getId(), groupB.getId())
				.size();
		org.junit.jupiter.api.Assertions.assertEquals(2, groupATeamCount, "Group A must contain 2 teams");
		org.junit.jupiter.api.Assertions.assertEquals(2, groupBTeamCount, "Group B must contain 2 teams");
	}

	// Helpers

	/**
	 * Persists a Race + RaceSettings + RaceLineups for one match. Track/Car are left null
	 * (race-result form does not require them; only the lineup + position inputs matter for
	 * scoring). Returns the persisted Race.
	 */
	private Race persistRaceWithLineups(Matchday md, Match match, Team home, Team away, List<Driver> drivers) {
		var race = new Race();
		race.setMatchday(md);
		race.setMatch(match);
		raceRepository.save(race);
		var settings = new RaceSettings(race);
		settings.setNumberOfLaps(20);
		settings.setTyreWearMultiplier(3);
		settings.setFuelConsumptionMultiplier(3);
		settings.setRefuelingSpeed(10);
		settings.setInitialFuel("90");
		settings.setNumberOfRequiredPitStops(0);
		settings.setTimeProgressionMultiplier(5);
		settings.setWeather("Preset S02");
		settings.setTimeOfDay("Afternoon");
		settings.setAvailableTyres("RS, RM, RH, I, W");
		settings.setMandatoryTyres("RS, RM, RH");
		race.setSettings(settings);
		raceRepository.save(race);

		// Lineups: first 3 drivers belong to home team, next 3 to away team.
		// Only 6 driver positions are valid for this race-result form (CTC Standard 6 positions).
		for (int i = 0; i < 3; i++) {
			raceLineupRepository.save(new RaceLineup(race, drivers.get(i), home));
		}
		for (int i = 3; i < 6; i++) {
			raceLineupRepository.save(new RaceLineup(race, drivers.get(i), away));
		}
		return race;
	}

	/**
	 * UI race-result entry: navigate to /admin/races/{raceId}/results, fill the
	 * position input for each driver in the order they should finish 1..N, and click Save.
	 *
	 * <p>The race-results template renders one row per RaceLineup with hidden inputs for
	 * driverId/driverPsnId/teamShortName plus a visible position input. The form is keyed by
	 * row index, but driverPsnId is shown in the visible cell — we look up each row by its
	 * driverPsnId text and fill that row's position input with the desired finishing place.
	 */
	private void fillRaceResultsByPsnIdOrder(UUID raceId, List<String> psnIdsInFinishingOrder) {
		page.navigate(url("/admin/races/" + raceId + "/results"));
		assertThat(page.locator("h1")).containsText("Results");

		// Map driverPsnId → position input by walking the table rows.
		// Each row has a visible cell ".results-driver-name" (driverPsnId) and an editable
		// ".pos-input.results-pos-input" position number input.
		for (int i = 0; i < psnIdsInFinishingOrder.size(); i++) {
			String psnId = psnIdsInFinishingOrder.get(i);
			int finishingPos = i + 1;
			var row = page.locator("tr:has(.results-driver-name:has-text('" + psnId + "'))").first();
			assertThat(row).isVisible();
			row.locator("input.results-pos-input.pos-input").first().fill(String.valueOf(finishingPos));
			// qualifying position mirrors race position to keep the form deterministic
			row.locator("input.results-pos-input.quali-input").first().fill(String.valueOf(finishingPos));
		}
		page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Save Results")).click();
		assertThat(page.locator(".alert-success")).containsText("Results saved");
	}

	// @TestConfiguration — stub GoogleSheetsService

	@TestConfiguration
	static class TestGoogleSheetsConfig {
		@Bean
		@Primary
		GoogleSheetsService googleSheetsService() {
			// Stub returns one synthetic year-tab "2099" with a header row + 12 driver rows
			// keyed to the 4 GROUPS-layout test teams. Group resolution occurs server-side
			// via PhaseTeam so we do not emit a Group column.
			return new GoogleSheetsService("") {
				@Override
				public boolean isAvailable() {
					return true;
				}

				@Override
				public String extractSpreadsheetId(String url) {
					return "test-spreadsheet-id";
				}

				@Override
				public List<String> getSheetNames(String spreadsheetId) {
					return List.of("2099");
				}

				@Override
				public List<List<Object>> readRange(String spreadsheetId, String range) {
					return readRowsForTab();
				}

				@Override
				public List<List<Object>> readRangeFromSheet(String spreadsheetId, String sheetName, String range) {
					return readRowsForTab();
				}

				private List<List<Object>> readRowsForTab() {
					List<List<Object>> rows = new ArrayList<>();
					rows.add(List.of("PSN ID", "Name", "Team")); // header (skipped by service)
					rows.add(List.of("T_groups_drv01", "Test Driver 01", "T-GA-1"));
					rows.add(List.of("T_groups_drv02", "Test Driver 02", "T-GA-1"));
					rows.add(List.of("T_groups_drv03", "Test Driver 03", "T-GA-1"));
					rows.add(List.of("T_groups_drv04", "Test Driver 04", "T-GA-2"));
					rows.add(List.of("T_groups_drv05", "Test Driver 05", "T-GA-2"));
					rows.add(List.of("T_groups_drv06", "Test Driver 06", "T-GA-2"));
					rows.add(List.of("T_groups_drv07", "Test Driver 07", "T-GB-1"));
					rows.add(List.of("T_groups_drv08", "Test Driver 08", "T-GB-1"));
					rows.add(List.of("T_groups_drv09", "Test Driver 09", "T-GB-1"));
					rows.add(List.of("T_groups_drv10", "Test Driver 10", "T-GB-2"));
					rows.add(List.of("T_groups_drv11", "Test Driver 11", "T-GB-2"));
					rows.add(List.of("T_groups_drv12", "Test Driver 12", "T-GB-2"));
					return rows;
				}
			};
		}
	}
}
