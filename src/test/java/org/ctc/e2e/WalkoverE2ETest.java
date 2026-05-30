package org.ctc.e2e;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import org.ctc.TestHelper;
import org.ctc.domain.model.Match;
import org.ctc.domain.model.Matchday;
import org.ctc.domain.model.Race;
import org.ctc.domain.model.Season;
import org.ctc.domain.model.Team;
import org.ctc.domain.repository.MatchRepository;
import org.ctc.domain.repository.MatchdayRepository;
import org.ctc.domain.repository.RaceRepository;
import org.ctc.domain.repository.SeasonRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@Tag("e2e")
class WalkoverE2ETest extends PlaywrightConfig {

	@Autowired
	TestHelper helper;

	@Autowired
	SeasonRepository seasonRepository;

	@Autowired
	MatchdayRepository matchdayRepository;

	@Autowired
	MatchRepository matchRepository;

	@Autowired
	RaceRepository raceRepository;

	@BeforeEach
	void setUp() {
		setupPage();
	}

	@AfterEach
	void tearDown() {
		teardownPage();
	}

	@Test
	void givenMatchEditForm_whenMarkWalkover_thenWoLabelAppearsInMatchdayDetail() {
		// given — a self-contained matchday with a real two-team match
		Season season = helper.createSeason("E2E Walkover");
		seasonRepository.save(season);
		Matchday md = helper.createMatchdayInRegularPhase(season, "MD-E2E-WO", 0);
		Team home = helper.createTeam("E2E WO Home", "ewoh");
		Team away = helper.createTeam("E2E WO Away", "ewoa");
		Match match = helper.createMatch(md, home, away);
		Race race = helper.createRace(md, match);
		raceRepository.save(race);
		match.getRaces().add(race);
		matchRepository.save(match);
		md.getMatches().add(match);
		matchdayRepository.save(md);

		// when — mark the away team as the walkover (forfeiter) via the edit form
		page.navigate(url("/admin/matches/" + match.getId() + "/edit"));
		page.selectOption("#walkoverTeamId", away.getId().toString());
		page.locator("[data-testid='match-edit-save']").click();

		// then — the matchday detail shows the "w/o" marker (forfeiter name + score column)
		page.navigate(url("/admin/matchdays/" + md.getId()));
		assertThat(page.locator(".match-wo").first()).hasText("w/o");
		assertThat(page.locator(".match-wo")).hasCount(2);
		// a settled walkover must NOT be flagged as an unplayed "Open" match
		assertThat(page.locator(".badge-inactive:has-text('Open')")).hasCount(0);
	}
}
