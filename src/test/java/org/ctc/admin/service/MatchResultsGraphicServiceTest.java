package org.ctc.admin.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.ctc.domain.model.*;
import org.ctc.domain.service.ScoringService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MatchResultsGraphicServiceTest {

	private final ScoringService scoringService = mock(ScoringService.class);
	@TempDir
	Path tempDir;

	private MatchResultsGraphicService createService() {
		return new MatchResultsGraphicService(null, scoringService, tempDir.toString());
	}

	private Race createRaceWithResults(Match match, Matchday matchday, Team homeTeam, int homePoints, int awayPoints) {
		var race = new Race();
		race.setId(UUID.randomUUID());
		race.setMatchday(matchday);
		race.setMatch(match);

		var homeDriver = new Driver();
		homeDriver.setId(UUID.randomUUID());
		homeDriver.setPsnId("HomeDriver");
		var homeResult = new RaceResult(race, homeDriver, 1, 1, false);
		homeResult.setPointsTotal(homePoints);

		var awayDriver = new Driver();
		awayDriver.setId(UUID.randomUUID());
		awayDriver.setPsnId("AwayDriver");
		var awayResult = new RaceResult(race, awayDriver, 2, 2, false);
		awayResult.setPointsTotal(awayPoints);

		race.getResults().addAll(List.of(homeResult, awayResult));

        when(scoringService.calculateRaceTotals(race, homeTeam.getId())).thenReturn(RacePenaltyTotals.calculate(homePoints, awayPoints, 0, 0));

		return race;
	}

    @Test
    void givenTieBreakMatch_whenBuildHtml_thenWinnerAndTieBreakBadge() throws Exception {
        // given
        var season = new Season("Test tie-break match"); season.setId(UUID.randomUUID());
        var day = org.ctc.domain.service.PhaseTestFixtures.matchdayInRegularPhase(season, "Test round", 1);
        var home = new Team("Test home", "TBH"); home.setId(UUID.randomUUID());
        var away = new Team("Test away", "TBA"); away.setId(UUID.randomUUID());
        var match = new Match(day, home, away); match.setId(UUID.randomUUID());
        match.setHomeScore(40); match.setAwayScore(40); match.setTieBreakWinner(home);
        match.getRaces().add(createRaceWithResults(match, day, home, 40, 40));
        var cards = tempDir.resolve("team-cards").resolve(season.getId().toString()); Files.createDirectories(cards);
        Files.write(cards.resolve("TBH.png"), new byte[]{1}); Files.write(cards.resolve("TBA.png"), new byte[]{1});
        Files.copy(Path.of("src/main/resources/templates/admin/match-results-render.html"), tempDir.resolve("match-results-template.html"));
        // when
        var html = createService().buildHtml(match);
        // then
        var doc = org.jsoup.Jsoup.parse(html);
        assertThat(html).contains("TIE-BREAK");
        assertThat(doc.select(".footer .team-total.winner")).as("only the tie-break winner is marked").hasSize(1);
        assertThat(doc.select(".footer .team-total").first().hasClass("winner")).as("home won").isTrue();
    }

    @Test
    void givenRegularDraw_whenBuildHtml_thenNoWinnerNoBadge() throws Exception {
        // given
        var season = new Season("Test draw match"); season.setId(UUID.randomUUID());
        var day = org.ctc.domain.service.PhaseTestFixtures.matchdayInRegularPhase(season, "Test round", 1);
        var home = new Team("Test home", "TDH"); home.setId(UUID.randomUUID());
        var away = new Team("Test away", "TDA"); away.setId(UUID.randomUUID());
        var match = new Match(day, home, away); match.setId(UUID.randomUUID());
        match.getRaces().add(createRaceWithResults(match, day, home, 40, 40));
        var cards = tempDir.resolve("team-cards").resolve(season.getId().toString()); Files.createDirectories(cards);
        Files.write(cards.resolve("TDH.png"), new byte[]{1}); Files.write(cards.resolve("TDA.png"), new byte[]{1});
        Files.copy(Path.of("src/main/resources/templates/admin/match-results-render.html"), tempDir.resolve("match-results-template.html"));
        // when
        var html = createService().buildHtml(match);
        // then
        assertThat(html).doesNotContain("TIE-BREAK");
        assertThat(org.jsoup.Jsoup.parse(html).select(".footer .team-total.winner")).isEmpty();
    }

    @Test
    void givenWalkoverWithTwoEmptyLegs_whenBuildingGraphic_thenOneTeamOnlyScoreWithoutBonuses() {
        var home = new Team("Test walkover home", "T-WOH"); home.setId(UUID.randomUUID());
        var away = new Team("Test walkover away", "T-WOA"); away.setId(UUID.randomUUID());
        var day = org.ctc.domain.service.PhaseTestFixtures.matchdayInRegularPhase(new Season("Test walkover season"), "Test round", 1);
        day.getPhase().setRaceScoring(new RaceScoring("Test walkover scoring", "20,17,14,12,10,8,7", "3,2,1", 2));
        var match = new Match(day, home, away); match.setWalkoverTeam(home);
        for (int leg = 0; leg < 2; leg++) {
            var race = new Race(); race.setMatch(match); race.setMatchday(day); match.getRaces().add(race);
        }
        var rows = createService().buildRaceRows(match);
        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row.homePoints()).isZero(); assertThat(row.awayPoints()).isEqualTo(81);
        });
        assertThat(match.getRaces()).allSatisfy(race -> assertThat(race.getResults()).isEmpty());
    }

    @Test void givenTwoPlayoffWalkoverLegsWithRegularAnchor_whenBuildingGraphic_thenScoreActualPairingOnce() {
        var home = new Team("Test home", "TestH"); home.setId(UUID.randomUUID());
        var away = new Team("Test away", "TestA"); away.setId(UUID.randomUUID());
        var day = org.ctc.domain.service.PhaseTestFixtures.matchdayInRegularPhase(new Season("Test WO"), "Test round", 1);
        var match = new Match(day, home, away); match.setId(UUID.randomUUID());
        var phase = new SeasonPhase(); phase.setRaceScoring(new RaceScoring("Test playoff", "10,9,8,7,6,5", "50", 50));
        var playoff = new Playoff(); playoff.setPhase(phase);
        var matchup = new PlayoffMatchup(new PlayoffRound(playoff, "Test final", 1), 0); matchup.setId(UUID.randomUUID());
        matchup.setTeam1(home); matchup.setTeam2(away); matchup.setWalkoverTeam(home); match.setWalkoverTeam(away);
        for (int i = 0; i < 2; i++) { var race = new Race(); race.setMatchday(day); race.setMatch(match); race.setPlayoffMatchup(matchup); match.getRaces().add(race); }
        assertThat(createService().buildRaceRows(match)).singleElement().satisfies(row -> {
            assertThat(row.homePoints()).isZero(); assertThat(row.awayPoints()).isEqualTo(45);
        });
    }

	@Test
	void givenMatchWith2Races_whenBuildRaceRows_thenReturns2RowsWithCorrectLabelsAndPoints() {
		// given
		var service = createService();
		var homeTeam = new Team("Home", "HOM");
		homeTeam.setId(UUID.randomUUID());
		var awayTeam = new Team("Away", "AWY");
		awayTeam.setId(UUID.randomUUID());

		var matchday = org.ctc.domain.service.PhaseTestFixtures.matchdayInRegularPhase(new Season("S"), "MD 1", 1);
		var match = new Match(matchday, homeTeam, awayTeam);
		match.setRaces(new ArrayList<>());

		var race1 = createRaceWithResults(match, matchday, homeTeam, 120, 98);
		var race2 = createRaceWithResults(match, matchday, homeTeam, 105, 112);
		match.getRaces().addAll(List.of(race1, race2));

		// when
		var rows = service.buildRaceRows(match);

		// then
		assertThat(rows).hasSize(2);
		assertThat(rows.get(0).label()).isEqualTo("Race 1");
		assertThat(rows.get(0).homePoints()).isEqualTo(120);
		assertThat(rows.get(0).awayPoints()).isEqualTo(98);
		assertThat(rows.get(1).label()).isEqualTo("Race 2");
		assertThat(rows.get(1).homePoints()).isEqualTo(105);
		assertThat(rows.get(1).awayPoints()).isEqualTo(112);
	}

	@Test
	void givenMatchWith3Races_whenBuildRaceRows_thenReturns3Rows() {
		// given
		var service = createService();
		var homeTeam = new Team("Home", "HOM");
		homeTeam.setId(UUID.randomUUID());
		var awayTeam = new Team("Away", "AWY");
		awayTeam.setId(UUID.randomUUID());

		var matchday = org.ctc.domain.service.PhaseTestFixtures.matchdayInRegularPhase(new Season("S"), "MD 1", 1);
		var match = new Match(matchday, homeTeam, awayTeam);
		match.setRaces(new ArrayList<>());

		match.getRaces().add(createRaceWithResults(match, matchday, homeTeam, 80, 90));
		match.getRaces().add(createRaceWithResults(match, matchday, homeTeam, 95, 85));
		match.getRaces().add(createRaceWithResults(match, matchday, homeTeam, 100, 100));

		// when
		var rows = service.buildRaceRows(match);

		// then
		assertThat(rows).hasSize(3);
		assertThat(rows.get(2).label()).isEqualTo("Race 3");
	}

	@Test
	void givenRaceWithoutResults_whenBuildRaceRows_thenSkipsRace() {
		// given
		var service = createService();
		var homeTeam = new Team("Home", "HOM");
		homeTeam.setId(UUID.randomUUID());
		var awayTeam = new Team("Away", "AWY");
		awayTeam.setId(UUID.randomUUID());

		var matchday = org.ctc.domain.service.PhaseTestFixtures.matchdayInRegularPhase(new Season("S"), "MD 1", 1);
		var match = new Match(matchday, homeTeam, awayTeam);
		match.setRaces(new ArrayList<>());

		match.getRaces().add(createRaceWithResults(match, matchday, homeTeam, 120, 98));

		// Race without results
		var emptyRace = new Race();
		emptyRace.setId(UUID.randomUUID());
		emptyRace.setMatchday(matchday);
		emptyRace.setMatch(match);
		match.getRaces().add(emptyRace);

		// when
		var rows = service.buildRaceRows(match);

		// then
		assertThat(rows).hasSize(1);
		assertThat(rows.get(0).label()).isEqualTo("Race 1");
	}

	@Test
	void whenNoCustomTemplate_thenHasCustomTemplateReturnsFalse() {
		var service = createService();
		assertThat(service.hasCustomTemplate()).isFalse();
	}

	@Test
	void givenCustomTemplate_whenHasCustomTemplate_thenReturnsTrue() throws IOException {
		var service = createService();
		Files.writeString(tempDir.resolve("match-results-template.html"), "<html>custom</html>");
		assertThat(service.hasCustomTemplate()).isTrue();
	}

	@Test
	void whenLoadTemplate_thenReturnsDefaultTemplate() throws IOException {
		var service = createService();
		String template = service.loadTemplate();
		assertThat(template).contains("raceRows");
	}

	@Test
	void givenTemplateContent_whenSaveTemplate_thenWritesFile() throws IOException {
		var service = createService();
		String content = "<html>saved match results</html>";
		service.saveTemplate(content);
		assertThat(Files.readString(tempDir.resolve("match-results-template.html"))).isEqualTo(content);
	}

	@Test
	void givenCustomTemplate_whenResetTemplate_thenDeletesFile() throws IOException {
		var service = createService();
		Files.writeString(tempDir.resolve("match-results-template.html"), "<html>custom</html>");
		service.resetTemplate();
		assertThat(service.hasCustomTemplate()).isFalse();
	}
}
