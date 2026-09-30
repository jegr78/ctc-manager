package org.ctc.dataimport;

import jakarta.persistence.EntityManager;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.ctc.TestHelper;
import org.ctc.domain.model.Driver;
import org.ctc.domain.model.Matchday;
import org.ctc.domain.model.PhaseLayout;
import org.ctc.domain.model.PhaseType;
import org.ctc.domain.model.RaceResult;
import org.ctc.domain.model.RaceScoring;
import org.ctc.domain.model.Season;
import org.ctc.domain.model.SeasonFormat;
import org.ctc.domain.model.SeasonPhase;
import org.ctc.domain.model.Team;
import org.ctc.domain.repository.MatchdayRepository;
import org.ctc.domain.repository.PlayoffMatchupRepository;
import org.ctc.domain.repository.RaceRepository;
import org.ctc.domain.repository.RaceScoringRepository;
import org.ctc.domain.repository.SeasonRepository;
import org.ctc.domain.service.PlayoffService;
import org.ctc.domain.service.SeasonPhaseService;
import org.ctc.testsupport.CtcDevSpringBootContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Imports the same scorecard into phases with different points tables and reads the stored
 * points back.
 */
@CtcDevSpringBootContext
@Tag("integration")
@Transactional
class CsvImportPhaseScoringIT {

	@Autowired CsvImportService csvImportService;
	@Autowired SeasonPhaseService seasonPhaseService;
	@Autowired PlayoffService playoffService;
	@Autowired TestHelper testHelper;
	@Autowired SeasonRepository seasonRepository;
	@Autowired MatchdayRepository matchdayRepository;
	@Autowired RaceRepository raceRepository;
	@Autowired RaceScoringRepository raceScoringRepository;
	@Autowired PlayoffMatchupRepository playoffMatchupRepository;
	@Autowired EntityManager entityManager;

	private final String id = UUID.randomUUID().toString().substring(0, 8);
	private Season season;
	private SeasonPhase regular;
	private Team home;
	private Team away;
	private Driver homeDriver;
	private Driver awayDriver;
	private RaceScoring otherScoring;

	@BeforeEach
	void createSeasonWithTwoTeams() {
		season = testHelper.createSeason("Test_PhaseScoring_" + id);
		home = testHelper.createTeam("Test PhaseScoring Home " + id, "Test_PSH_" + id);
		away = testHelper.createTeam("Test PhaseScoring Away " + id, "Test_PSA_" + id);
		season.addTeam(home);
		season.addTeam(away);
		seasonRepository.save(season);
		regular = seasonPhaseService.findRegularPhase(season.getId());
		homeDriver = testHelper.createDriver("Test_PhaseScoring_" + id + "_H", "Test PhaseScoring Home Driver");
		awayDriver = testHelper.createDriver("Test_PhaseScoring_" + id + "_A", "Test PhaseScoring Away Driver");
		testHelper.createSeasonDriver(season, homeDriver, home);
		testHelper.createSeasonDriver(season, awayDriver, away);
		otherScoring = raceScoringRepository.save(new RaceScoring("Test_PhaseScoring " + id, "50,30,20", "5,4,3", 7));
	}

	@Test
	void givenPlacementPhaseWithItsOwnScoring_whenImportedIntoItsMatchday_thenItsPointsTableIsUsed() throws Exception {
		// given
		var placement = seasonPhaseService.create(season.getId(), PhaseType.PLACEMENT, PhaseLayout.LEAGUE, 5,
				"Placement", otherScoring, regular.getMatchScoring(), SeasonFormat.LEAGUE, null, null, null, 1, null);
		var matchday = matchdayRepository.save(new Matchday(placement, "Test_PhaseScoring PL " + id, 50));

		// when
		importScorecard(new CsvImportService.ImportMetadata(season.getId(), null, null, null, null, matchday.getId()));

		// then
		assertThat(pointsOf(homeDriver)).as("winner: 50 race + 5 quali + 7 fastest lap").isEqualTo(62);
		assertThat(pointsOf(awayDriver)).as("runner-up: 30 race + 4 quali").isEqualTo(34);
		var match = raceRepository.findByMatchdayId(matchday.getId()).getFirst().getMatch();
		assertThat(match.getHomeScore()).as("match home total").isEqualTo(62);
		assertThat(match.getAwayScore()).as("match away total").isEqualTo(34);
	}

	@Test
	void givenRegularPhase_whenImportedIntoItsMatchday_thenTheRegularPointsTableIsUsed() throws Exception {
		// given
		var matchday = testHelper.createMatchdayInRegularPhase(season, "Test_PhaseScoring R " + id, 1);

		// when
		importScorecard(new CsvImportService.ImportMetadata(season.getId(), null, null, null, null, matchday.getId()));

		// then
		assertThat(pointsOf(homeDriver)).as("winner: 20 race + 3 quali + 2 fastest lap").isEqualTo(25);
		assertThat(pointsOf(awayDriver)).as("runner-up: 17 race + 2 quali").isEqualTo(19);
	}

	@Test
	void givenPlayoffWithItsOwnScoring_whenImportedForAMatchup_thenThePlayoffPointsTableIsUsed() throws Exception {
		// given
		var playoff = playoffService.createPlayoff(season.getId(), "Test PhaseScoring Playoff " + id, 2);
		playoff.getPhase().setRaceScoring(otherScoring);
		var matchup = playoffMatchupRepository.findByRoundPlayoffId(playoff.getId()).getFirst();
		matchup.setTeam1(home);
		matchup.setTeam2(away);
		entityManager.flush();

		// when
		importScorecard(new CsvImportService.ImportMetadata(season.getId(), "Test_PhaseScoring PO " + id, null, null,
				matchup.getId(), null));

		// then
		assertThat(pointsOf(homeDriver)).as("winner: 50 race + 5 quali + 7 fastest lap").isEqualTo(62);
		assertThat(pointsOf(awayDriver)).as("runner-up: 30 race + 4 quali").isEqualTo(34);
	}

	@Test
	void givenTargetPhaseWithoutScoring_whenImported_thenRejectedWithoutFallingBackToRegular() {
		// given
		var placement = seasonPhaseService.create(season.getId(), PhaseType.PLACEMENT, PhaseLayout.LEAGUE, 5,
				"Placement", null, regular.getMatchScoring(), SeasonFormat.LEAGUE, null, null, null, 1, null);
		var matchday = matchdayRepository.save(new Matchday(placement, "Test_PhaseScoring NS " + id, 60));

		// when / then
		assertThatThrownBy(() -> importScorecard(
				new CsvImportService.ImportMetadata(season.getId(), null, null, null, null, matchday.getId())))
				.isInstanceOf(org.ctc.dataimport.exception.ImportRejectedException.class)
				.satisfies(ex -> assertThat(((org.ctc.dataimport.exception.ImportRejectedException) ex).getErrors())
						.containsExactly("The target phase has no race scoring"));
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
		entityManager.clear();
	}

	private int pointsOf(Driver driver) {
		return entityManager.createQuery("select r from RaceResult r where r.driver.id = :id", RaceResult.class)
				.setParameter("id", driver.getId())
				.getSingleResult()
				.getPointsTotal();
	}
}
