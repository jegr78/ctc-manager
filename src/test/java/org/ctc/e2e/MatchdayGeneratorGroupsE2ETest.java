package org.ctc.e2e;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.microsoft.playwright.options.AriaRole;
import java.nio.file.Paths;
import java.util.UUID;
import org.ctc.domain.model.PhaseLayout;
import org.ctc.domain.model.PhaseTeam;
import org.ctc.domain.model.PhaseType;
import org.ctc.domain.model.Season;
import org.ctc.domain.model.SeasonPhase;
import org.ctc.domain.model.SeasonPhaseGroup;
import org.ctc.domain.model.SeasonTeam;
import org.ctc.domain.model.Team;
import org.ctc.domain.repository.MatchdayRepository;
import org.ctc.domain.repository.PhaseTeamRepository;
import org.ctc.domain.repository.SeasonPhaseGroupRepository;
import org.ctc.domain.repository.SeasonPhaseRepository;
import org.ctc.domain.repository.SeasonRepository;
import org.ctc.domain.repository.SeasonTeamRepository;
import org.ctc.domain.repository.TeamRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * QUAL-03 E2E gate: navigates {@code /admin/seasons/{id}/generate} for a GROUPS-layout Season,
 * asserts the per-group {@code <select>} is rendered, selects one group, submits, and verifies
 * the resulting matchdays are bound to the chosen group only.
 *
 * <p>Test-prefix fixture: Season 2098-1 (Test-prefix per CLAUDE.md "Isolate Test Data Completely").
 */
@Tag("e2e")
class MatchdayGeneratorGroupsE2ETest extends PlaywrightConfig {

	@Autowired private SeasonRepository seasonRepository;
	@Autowired private SeasonPhaseRepository seasonPhaseRepository;
	@Autowired private SeasonPhaseGroupRepository seasonPhaseGroupRepository;
	@Autowired private TeamRepository teamRepository;
	@Autowired private SeasonTeamRepository seasonTeamRepository;
	@Autowired private PhaseTeamRepository phaseTeamRepository;
	@Autowired private MatchdayRepository matchdayRepository;
	@Autowired private TransactionTemplate txTemplate;

	private Season season;
	private SeasonPhase regularPhase;
	private SeasonPhaseGroup groupA;
	private SeasonPhaseGroup groupB;

	@BeforeEach
	void seedFixture() {
		setupPage();
		txTemplate.executeWithoutResult(tx -> {
			// Idempotent cleanup: remove any leftover Test-prefix 2098 season from a previous run
			seasonRepository.findByYearAndNumber(2098, 1).forEach(s -> {
				phaseTeamRepository.findAll().stream()
						.filter(pt -> pt.getPhase().getSeason().getId().equals(s.getId()))
						.forEach(phaseTeamRepository::delete);
				seasonTeamRepository.findAll().stream()
						.filter(st -> st.getSeason().getId().equals(s.getId()))
						.forEach(seasonTeamRepository::delete);
				seasonPhaseGroupRepository.findAll().stream()
						.filter(g -> g.getPhase().getSeason().getId().equals(s.getId()))
						.forEach(seasonPhaseGroupRepository::delete);
				seasonPhaseRepository.findAll().stream()
						.filter(p -> p.getSeason().getId().equals(s.getId()))
						.forEach(seasonPhaseRepository::delete);
				seasonRepository.delete(s);
			});
			// Clean up Test-prefix teams from prior runs (idempotent)
			for (String shortName : java.util.List.of("P83Q3-A1", "P83Q3-A2", "P83Q3-B1", "P83Q3-B2")) {
				teamRepository.findByShortName(shortName).ifPresent(teamRepository::delete);
			}
		});

		txTemplate.executeWithoutResult(tx -> {
			season = seasonRepository.save(new Season("Phase83-Test-Q3-Season-2098", 2098, 1));
			regularPhase = seasonPhaseRepository.save(new SeasonPhase(season, PhaseType.REGULAR, PhaseLayout.GROUPS, 0));
			groupA = seasonPhaseGroupRepository.save(new SeasonPhaseGroup(regularPhase, "Group A", 0));
			groupB = seasonPhaseGroupRepository.save(new SeasonPhaseGroup(regularPhase, "Group B", 1));

			var teamA1 = teamRepository.save(new Team("Phase83-Q3-A1", "P83Q3-A1"));
			var teamA2 = teamRepository.save(new Team("Phase83-Q3-A2", "P83Q3-A2"));
			var teamB1 = teamRepository.save(new Team("Phase83-Q3-B1", "P83Q3-B1"));
			var teamB2 = teamRepository.save(new Team("Phase83-Q3-B2", "P83Q3-B2"));
			for (Team t : java.util.List.of(teamA1, teamA2, teamB1, teamB2)) {
				seasonTeamRepository.save(new SeasonTeam(season, t));
				var pt = new PhaseTeam(regularPhase, t);
				pt.setGroup(t.getShortName().contains("-A") ? groupA : groupB);
				phaseTeamRepository.save(pt);
			}
		});
	}

	@AfterEach
	void tearDown() {
		teardownPage();
		// Idempotent cleanup per CLAUDE.md "Isolate Test Data Completely" — drop everything we created
		// so subsequent tests (especially BackupImportE2ETest which round-trips ALL entities) see a
		// clean fixture.
		txTemplate.executeWithoutResult(tx -> {
			seasonRepository.findByYearAndNumber(2098, 1).forEach(s -> {
				matchdayRepository.findAll().stream()
						.filter(m -> m.getPhase().getSeason().getId().equals(s.getId()))
						.forEach(matchdayRepository::delete);
				phaseTeamRepository.findAll().stream()
						.filter(pt -> pt.getPhase().getSeason().getId().equals(s.getId()))
						.forEach(phaseTeamRepository::delete);
				seasonTeamRepository.findAll().stream()
						.filter(st -> st.getSeason().getId().equals(s.getId()))
						.forEach(seasonTeamRepository::delete);
				seasonPhaseGroupRepository.findAll().stream()
						.filter(g -> g.getPhase().getSeason().getId().equals(s.getId()))
						.forEach(seasonPhaseGroupRepository::delete);
				seasonPhaseRepository.findAll().stream()
						.filter(p -> p.getSeason().getId().equals(s.getId()))
						.forEach(seasonPhaseRepository::delete);
				seasonRepository.delete(s);
			});
			for (String shortName : java.util.List.of("P83Q3-A1", "P83Q3-A2", "P83Q3-B1", "P83Q3-B2")) {
				teamRepository.findByShortName(shortName).ifPresent(teamRepository::delete);
			}
		});
	}

	@Test
	void givenGroupsLayoutSeason_whenGenerateMatchdaysForGroupA_thenMatchdaysAreBoundToGroupA() {
		// given — navigate to the generate form
		page.navigate(url("/admin/seasons/" + season.getId() + "/generate"));

		// then — group select is rendered with Group A + Group B
		var groupSelect = page.locator("#groupId");
		assertThat(groupSelect).isVisible();
		assertThat(groupSelect.locator("option")).hasCount(2);
		assertThat(groupSelect).containsText("Group A");
		assertThat(groupSelect).containsText("Group B");

		// when — pick Group A, set rounds to 1, submit
		groupSelect.selectOption(groupA.getId().toString());
		page.locator("#numberOfRounds").fill("1");
		page.getByRole(AriaRole.BUTTON, new com.microsoft.playwright.Page.GetByRoleOptions().setName("Generate")).click();
		page.waitForLoadState();

		// then — assert observable outcome on landing page (MD 1 link rendered after the generate redirect chain).
		// Cannot assert on .alert-success because SeasonController#detail does a second redirect
		// to /admin/seasons/{id}/phases/{phaseId} that consumes the flash attribute.
		page.screenshot(new com.microsoft.playwright.Page.ScreenshotOptions()
				.setPath(Paths.get(".screenshots/qual-03-matchday-generator-groups.png"))
				.setFullPage(true));
		assertThat(page.locator("a").filter(new com.microsoft.playwright.Locator.FilterOptions().setHasText("MD 1")).first())
				.isVisible();

		// visual evidence
		page.screenshot(new com.microsoft.playwright.Page.ScreenshotOptions()
				.setPath(Paths.get(".screenshots/qual-03-matchday-generator-groups.png"))
				.setFullPage(true));

		// data assertion — matchdays exist for Group A, none for Group B
		txTemplate.executeWithoutResult(tx -> {
			var groupAMatchdays = matchdayRepository.findByGroupId(groupA.getId());
			var groupBMatchdays = matchdayRepository.findByGroupId(groupB.getId());
			if (groupAMatchdays.isEmpty()) {
				throw new AssertionError("Expected matchdays for Group A, got none");
			}
			if (!groupBMatchdays.isEmpty()) {
				throw new AssertionError("Expected no matchdays for Group B, got " + groupBMatchdays.size());
			}
			// Sanity: every Group-A matchday is bound to the regular phase
			UUID expectedPhaseId = regularPhase.getId();
			groupAMatchdays.forEach(m -> {
				if (!m.getPhase().getId().equals(expectedPhaseId)) {
					throw new AssertionError("Matchday " + m.getId() + " not bound to regular phase");
				}
			});
		});
	}
}
