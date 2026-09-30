package org.ctc.dataimport;

import jakarta.persistence.EntityManager;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.ctc.TestHelper;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
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
import org.ctc.domain.service.PlayoffDecisionGuard;
import org.ctc.domain.service.PlayoffService;
import org.ctc.testsupport.CtcDevSpringBootContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
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
		assertThatCode(() -> importScorecard(metadata(null, matchup.getId(), playoffMatchday.getId())))
				.as("the reversed listing is accepted").doesNotThrowAnyException();

		// then
		var races = raceRepository.findByPlayoffMatchupId(matchup.getId());
		assertThat(races).as("the leg is linked to the matchup").hasSize(1);
		assertThat(races.getFirst().getMatchday().getId()).as("on the selected playoff matchday")
				.isEqualTo(playoffMatchday.getId());
	}

	@Test
	void givenScorecardWithAThirdUnknownTeamBlock_whenImportedForAMatchup_thenRejectedWithoutWrites() throws Exception {
		// given
		var matchup = matchup(season, home, away);
		var csv = scorecard(home.getShortName(), away.getShortName()) + "Test_Unknown_" + id + ",Test_Target_" + id + "_X,3,3,false\n";

		// when
		var rejection = rejectionOf(() -> importCsv(csv, metadata("Test_Target PO " + id, matchup.getId(), null), false));

		// then
		assertThat(rejection.getErrors()).as("errors").containsExactly(
				"The scorecard teams " + home.getShortName() + " and " + away.getShortName() + " and Test_Unknown_" + id
						+ " are not the teams of the playoff matchup");
		assertNothingImportedFor(matchup);
	}

	@Test
	void givenMatchupWithOnlyOneTeam_whenImportedWithThatTeamAlone_thenRejectedWithoutWrites() throws Exception {
		// given
		var matchup = matchup(season, home, null);
		var csv = """
				Team,PSN ID,Position,Quali,FL
				%s,%s,1,1,true
				""".formatted(home.getShortName(), homeDriver.getPsnId());

		// when
		var rejection = rejectionOf(() -> importCsv(csv, metadata("Test_Target PO " + id, matchup.getId(), null), false));

		// then
		assertThat(rejection.getErrors()).as("errors").containsExactly(
				"The scorecard teams " + home.getShortName() + " are not the teams of the playoff matchup");
		assertNothingImportedFor(matchup);
	}

	@Test
	void givenMatchupWithOnlyOneTeam_whenImportedWithThatTeamAndItsSubTeam_thenRejectedWithoutWrites() throws Exception {
		// given
		var subTeam = testHelper.createSubTeam("Test Target Home Sub " + id, "Test_TGS_" + id, home);
		season.addTeam(subTeam);
		seasonRepository.save(season);
		var matchup = matchup(season, home, null);

		// when
		var rejection = rejectionOf(() -> importCsv(scorecard(home.getShortName(), subTeam.getShortName()),
				metadata("Test_Target PO " + id, matchup.getId(), null), false));

		// then
		assertThat(rejection.getErrors()).as("errors").containsExactly(
				"The scorecard teams " + home.getShortName() + " and " + subTeam.getShortName()
						+ " are not the teams of the playoff matchup");
		assertNothingImportedFor(matchup);
	}

	@Test
	void givenSubTeamOfAMatchupTeam_whenImportedForTheMatchup_thenTheLegIsStored() throws Exception {
		// given
		var subTeam = testHelper.createSubTeam("Test Target Home Sub " + id, "Test_TGS_" + id, home);
		season.addTeam(subTeam);
		seasonRepository.save(season);
		var subDriver = testHelper.createDriver("Test_Target_" + id + "_S", "Test Target Sub Driver");
		testHelper.createSeasonDriver(season, subDriver, subTeam);
		var matchup = matchup(season, home, away);
		var csv = """
				Team,PSN ID,Position,Quali,FL
				%s,%s,1,1,true
				%s,%s,2,2,false
				""".formatted(subTeam.getShortName(), subDriver.getPsnId(), away.getShortName(), awayDriver.getPsnId());

		// when / then
		assertThatCode(() -> importCsv(csv, metadata("Test_Target PO " + id, matchup.getId(), null), false))
				.as("the sub-team scorecard is accepted").doesNotThrowAnyException();

		// then
		assertThat(raceRepository.findByPlayoffMatchupId(matchup.getId())).as("a sub-team plays for its parent team")
				.hasSize(1);
	}

	@Test
	void givenPairingWhoseLegBelongsToAnotherMatchup_whenOverwrittenForThisMatchup_thenRejectedAndTheLegStays() throws Exception {
		// given
		var playoff = playoffService.createPlayoff(season.getId(), "Test Target Playoff " + id, 4);
		var matchups = playoffMatchupRepository.findByRoundPlayoffId(playoff.getId());
		var semi = matchups.stream().filter(m -> m.getRound().getRoundIndex() == 0).findFirst().orElseThrow();
		var decider = matchups.stream().filter(m -> m.getRound().getRoundIndex() == 1).findFirst().orElseThrow();
		semi.setTeam1(home);
		semi.setTeam2(away);
		decider.setTeam1(home);
		decider.setTeam2(away);
		var playoffMatchday = matchdayRepository.save(new Matchday(playoff.getPhase(), "Test_Target PMD " + id, 100));
		entityManager.flush();
		importScorecard(metadata(null, semi.getId(), playoffMatchday.getId()));

		// when
		var rejection = rejectionOf(() -> importCsv(scorecard(home.getShortName(), away.getShortName()),
				metadata(null, decider.getId(), playoffMatchday.getId()), true));

		// then
		assertThat(rejection.getErrors()).as("errors").containsExactly(
				"The pairing's legs belong to another playoff matchup");
		assertThat(raceRepository.findByPlayoffMatchupId(semi.getId())).as("the other matchup keeps its leg").hasSize(1);
		assertThat(raceRepository.findByPlayoffMatchupId(decider.getId())).as("no leg for this matchup").isEmpty();
	}

	@Test
	void givenDecidedMatchupOfAnotherSeason_whenImported_thenBothErrorsAreReported() throws Exception {
		// given
		var other = testHelper.createSeason("Test_Target_Other_" + id);
		other.addTeam(home);
		other.addTeam(away);
		seasonRepository.save(other);
		var foreignMatchup = matchup(other, home, away);
		foreignMatchup.setWinner(home);
		entityManager.flush();

		// when
		var rejection = rejectionOf(metadata("Test_Target PO " + id, foreignMatchup.getId(), null));

		// then
		assertThat(rejection.getErrors()).as("errors").containsExactlyInAnyOrder(PlayoffDecisionGuard.DECIDED,
				"The playoff matchup does not belong to the selected season");
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
		return rejectionOf(() -> importScorecard(metadata));
	}

	private ImportRejectedException rejectionOf(ThrowingCallable importCall) {
		var rejection = catchThrowableOfType(ImportRejectedException.class, importCall);
		assertThat(rejection).as("the import is rejected").isNotNull();
		return rejection;
	}

	private void importScorecard(CsvImportService.ImportMetadata metadata) throws Exception {
		importCsv(scorecard(home.getShortName(), away.getShortName()), metadata, false);
	}

	private String scorecard(String homeName, String awayName) {
		return """
				Team,PSN ID,Position,Quali,FL
				%s,%s,1,1,true
				%s,%s,2,2,false
				""".formatted(homeName, homeDriver.getPsnId(), awayName, awayDriver.getPsnId());
	}

	private void importCsv(String csv, CsvImportService.ImportMetadata metadata, boolean overwrite) throws Exception {
		var preview = csvImportService.parseAndPreview(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)), metadata);
		csvImportService.executeImport(preview, Map.of(), Set.of(), overwrite);
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
