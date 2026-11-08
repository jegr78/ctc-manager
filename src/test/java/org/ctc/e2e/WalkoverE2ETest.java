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
    @Autowired org.ctc.domain.repository.TeamRepository teams;
    private Season ownedSeason;
    private final java.util.List<Team> ownedTeams = new java.util.ArrayList<>();

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
        if (ownedSeason != null) helper.deleteSeasonCascade(ownedSeason);
        teams.deleteAll(ownedTeams);
	}

	@Test
	void givenMatchEditForm_whenMarkWalkover_thenWoLabelAppearsInMatchdayDetail() {
		// given — a self-contained matchday with a real two-team match
		Season season = helper.createSeason("Test_Walkover_" + java.util.UUID.randomUUID());
        ownedSeason = season;
		seasonRepository.save(season);
		Matchday md = helper.createMatchdayInRegularPhase(season, "MD-E2E-WO", 0);
		Team home = helper.createTeam("Test WO Home", "Test_woh_" + season.getId().toString().substring(0,8));
        ownedTeams.add(home);
		Team away = helper.createTeam("Test WO Away", "Test_woa_" + season.getId().toString().substring(0,8));
        ownedTeams.add(away);
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
        assertThat(page.locator(".match-score-value")).hasText(new String[]{"81", "0"});
        try {
            java.nio.file.Files.createDirectories(java.nio.file.Path.of(".screenshots"));
            page.screenshot(new com.microsoft.playwright.Page.ScreenshotOptions().setPath(java.nio.file.Path.of(".screenshots/walkover-desktop.png")).setFullPage(true).setAnimations(com.microsoft.playwright.options.ScreenshotAnimations.DISABLED));
            page.setViewportSize(390, 844);
            page.reload();
            assertThat(page.locator(".match-score-value")).hasText(new String[]{"81", "0"});
            page.screenshot(new com.microsoft.playwright.Page.ScreenshotOptions().setPath(java.nio.file.Path.of(".screenshots/walkover-mobile.png")).setFullPage(true).setAnimations(com.microsoft.playwright.options.ScreenshotAnimations.DISABLED));
        } catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
	}
}
