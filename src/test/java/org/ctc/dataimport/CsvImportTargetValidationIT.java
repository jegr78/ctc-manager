package org.ctc.dataimport;

import jakarta.persistence.EntityManager;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.ctc.TestHelper;
import org.ctc.dataimport.exception.ImportRejectedException;
import org.ctc.domain.model.Driver;
import org.ctc.domain.model.Matchday;
import org.ctc.domain.model.PlayoffMatchup;
import org.ctc.domain.model.Season;
import org.ctc.domain.model.Team;
import org.ctc.domain.repository.MatchdayRepository;
import org.ctc.domain.repository.PlayoffMatchupRepository;
import org.ctc.domain.repository.RaceRepository;
import org.ctc.domain.repository.SeasonRepository;
import org.ctc.domain.service.PlayoffService;
import org.ctc.testsupport.CtcDevSpringBootContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * Imports scorecards with contradictory target metadata and checks that the import is rejected
 * before it writes anything.
 */
@CtcDevSpringBootContext
@Tag("integration")
@Transactional
class CsvImportTargetValidationIT {

	@Autowired CsvImportService csvImportService;
	@Autowired PlayoffService playoffService;
	@Autowired TestHelper testHelper;
	@Autowired SeasonRepository seasonRepository;
	@Autowired MatchdayRepository matchdayRepository;
	@Autowired PlayoffMatchupRepository playoffMatchupRepository;
	@Autowired RaceRepository raceRepository;
	@Autowired EntityManager entityManager;

	private final String id = UUID.randomUUID().toString().substring(0, 8);
	private Season season;
	private Team home;
	private Team away;
	private Team third;
	private Driver homeDriver;
	private Driver awayDriver;

	@BeforeEach
	void createSeasonWithThreeTeams() {
		season = testHelper.createSeason("Test_Target_" + id);
		home = testHelper.createTeam("Test Target Home " + id, "Test_TGH_" + id);
		away = testHelper.createTeam("Test Target Away " + id, "Test_TGA_" + id);
		third = testHelper.createTeam("Test Target Third " + id, "Test_TGT_" + id);
		season.addTeam(home);
		season.addTeam(away);
		season.addTeam(third);
		seasonRepository.save(season);
		homeDriver = testHelper.createDriver("Test_Target_" + id + "_H", "Test Target Home Driver");
		awayDriver = testHelper.createDriver("Test_Target_" + id + "_A", "Test Target Away Driver");
		testHelper.createSeasonDriver(season, homeDriver, home);
		testHelper.createSeasonDriver(season, awayDriver, away);
	}

	@Test
	void givenPlayoffMatchupOfAnotherSeason_whenImported_thenRejectedWithoutWrites() throws Exception {
		// given
		var other = testHelper.createSeason("Test_Target_Other_" + id);
		other.addTeam(home);
		other.addTeam(away);
		seasonRepository.save(other);
		var foreignMatchup = matchup(other, home, away);

		// when
		var rejection = rejectionOf(metadata("Test_Target PO " + id, foreignMatchup.getId(), null));

		// then
		assertThat(rejection.getErrors()).as("errors").containsExactly(
				"The playoff matchup does not belong to the selected season");
		assertNothingImportedFor(foreignMatchup);
		assertNoMatchdayIn(foreignMatchup);
	}

	@Test
	void givenPlayoffMatchupOfOtherTeams_whenImported_thenRejectedWithoutWrites() throws Exception {
		// given
		var wrongMatchup = matchup(season, home, third);

		// when
		var rejection = rejectionOf(metadata("Test_Target PO " + id, wrongMatchup.getId(), null));

		// then
		assertThat(rejection.getErrors()).as("errors").containsExactly(
				"The scorecard teams " + home.getShortName() + " and " + away.getShortName()
						+ " are not the teams of the playoff matchup");
		assertNothingImportedFor(wrongMatchup);
		assertNoMatchdayIn(wrongMatchup);
	}

	@Test
	void givenPlayoffMatchupWithoutTeams_whenImported_thenRejectedWithoutWrites() throws Exception {
		// given
		var openMatchup = matchup(season, null, null);

		// when
		var rejection = rejectionOf(metadata("Test_Target PO " + id, openMatchup.getId(), null));

		// then
		assertThat(rejection.getErrors()).as("errors").containsExactly(
				"The scorecard teams " + home.getShortName() + " and " + away.getShortName()
						+ " are not the teams of the playoff matchup");
		assertNothingImportedFor(openMatchup);
		assertNoMatchdayIn(openMatchup);
	}

	@Test
	void givenUnknownPlayoffMatchup_whenImported_thenRejectedWithoutWrites() throws Exception {
		// when
		var rejection = rejectionOf(metadata("Test_Target PO " + id, UUID.randomUUID(), null));

		// then
		assertThat(rejection.getErrors()).as("errors").containsExactly("The playoff matchup does not exist");
		assertThat(matchdayRepository.findBySeasonIdOrderBySortIndexAsc(season.getId())).as("no matchday created").isEmpty();
	}

	@Test
	void givenRegularMatchdayWithPlayoffMatchup_whenImported_thenRejectedWithoutWrites() throws Exception {
		// given
		var regularMatchday = testHelper.createMatchdayInRegularPhase(season, "Test_Target R " + id, 1);
		var matchup = matchup(season, home, away);

		// when
		var rejection = rejectionOf(metadata(null, matchup.getId(), regularMatchday.getId()));

		// then
		assertThat(rejection.getErrors()).as("errors").containsExactly(
				"The matchday does not belong to the playoff matchup's phase");
		assertNothingImportedFor(matchup);
		assertThat(raceRepository.findByMatchdayId(regularMatchday.getId())).as("no race on the regular matchday").isEmpty();
	}

	@Test
	void givenPlayoffMatchdayWithoutPlayoffMatchup_whenImported_thenRejectedWithoutWrites() throws Exception {
		// given
		var matchup = matchup(season, home, away);
		var playoffMatchday = matchdayRepository.save(
				new Matchday(matchup.getRound().getPlayoff().getPhase(), "Test_Target PMD " + id, 100));

		// when
		var rejection = rejectionOf(metadata(null, null, playoffMatchday.getId()));

		// then
		assertThat(rejection.getErrors()).as("errors").containsExactly(
				"A playoff matchday needs a playoff matchup");
		assertThat(raceRepository.findByMatchdayId(playoffMatchday.getId())).as("no race on the playoff matchday").isEmpty();
	}

	@Test
	void givenForeignMatchdayAndForeignMatchup_whenImported_thenAllErrorsAreReported() throws Exception {
		// given
		var other = testHelper.createSeason("Test_Target_Other_" + id);
		other.addTeam(home);
		other.addTeam(away);
		seasonRepository.save(other);
		var foreignMatchup = matchup(other, home, away);
		var foreignMatchday = matchdayRepository.save(
				new Matchday(foreignMatchup.getRound().getPlayoff().getPhase(), "Test_Target FMD " + id, 100));

		// when
		var rejection = rejectionOf(metadata(null, foreignMatchup.getId(), foreignMatchday.getId()));

		// then
		assertThat(rejection.getErrors()).as("errors").containsExactlyInAnyOrder(
				"The playoff matchup does not belong to the selected season",
				"The matchday does not belong to the selected season");
		assertNothingImportedFor(foreignMatchup);
	}

	@Test
	void givenPlayoffMatchdayAndItsMatchupWithReversedTeams_whenImported_thenTheLegIsStored() throws Exception {
		// given
		var matchup = matchup(season, away, home);
		var playoffMatchday = matchdayRepository.save(
				new Matchday(matchup.getRound().getPlayoff().getPhase(), "Test_Target PMD " + id, 100));

		// when
		importScorecard(metadata(null, matchup.getId(), playoffMatchday.getId()));

		// then
		var races = raceRepository.findByPlayoffMatchupId(matchup.getId());
		assertThat(races).as("the leg is linked to the matchup").hasSize(1);
		assertThat(races.getFirst().getMatchday().getId()).as("on the selected playoff matchday")
				.isEqualTo(playoffMatchday.getId());
	}

	private PlayoffMatchup matchup(Season target, Team team1, Team team2) {
		var playoff = playoffService.createPlayoff(target.getId(), "Test Target Playoff " + id, 2);
		var matchup = playoffMatchupRepository.findByRoundPlayoffId(playoff.getId()).getFirst();
		matchup.setTeam1(team1);
		matchup.setTeam2(team2);
		entityManager.flush();
		return matchup;
	}

	private CsvImportService.ImportMetadata metadata(String label, UUID matchupId, UUID matchdayId) {
		return new CsvImportService.ImportMetadata(season.getId(), label, null, null, matchupId, matchdayId);
	}

	private ImportRejectedException rejectionOf(CsvImportService.ImportMetadata metadata) {
		var rejection = catchThrowableOfType(ImportRejectedException.class, () -> importScorecard(metadata));
		assertThat(rejection).as("the import is rejected").isNotNull();
		return rejection;
	}

	private void importScorecard(CsvImportService.ImportMetadata metadata) throws Exception {
		String csv = """
				Team,PSN ID,Position,Quali,FL
				%s,%s,1,1,true
				%s,%s,2,2,false
				""".formatted(home.getShortName(), homeDriver.getPsnId(), away.getShortName(), awayDriver.getPsnId());
		var preview = csvImportService.parseAndPreview(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)), metadata);
		csvImportService.executeImport(preview, Map.of(), Set.of(), false);
		entityManager.flush();
	}

	private void assertNothingImportedFor(PlayoffMatchup matchup) {
		assertThat(raceRepository.findByPlayoffMatchupId(matchup.getId())).as("no race for the matchup").isEmpty();
		assertThat(raceRepository.findAll().stream().filter(race -> race.getResults().stream()
				.anyMatch(result -> List.of(homeDriver.getId(), awayDriver.getId()).contains(result.getDriver().getId()))))
				.as("no race carries the scorecard's results").isEmpty();
	}

	private void assertNoMatchdayIn(PlayoffMatchup matchup) {
		assertThat(matchdayRepository.findByPhaseIdOrderBySortIndexAsc(matchup.getRound().getPlayoff().getPhase().getId()))
				.as("no matchday created in the playoff phase").isEmpty();
	}
}
