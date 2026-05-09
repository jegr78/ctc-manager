package org.ctc.admin.service;

import org.ctc.domain.model.*;
import org.ctc.domain.service.ScoringService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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

		when(scoringService.isDriverInTeam(eq(homeResult), any(), eq(homeTeam.getId()))).thenReturn(true);
		when(scoringService.isDriverInTeam(eq(awayResult), any(), eq(homeTeam.getId()))).thenReturn(false);

		return race;
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
