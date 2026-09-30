package org.ctc.domain.service;

import jakarta.persistence.EntityManager;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.ctc.TestHelper;
import org.ctc.domain.model.Driver;
import org.ctc.domain.model.Match;
import org.ctc.domain.model.Matchday;
import org.ctc.domain.model.Race;
import org.ctc.domain.model.RaceLineup;
import org.ctc.domain.model.Season;
import org.ctc.domain.model.Team;
import org.ctc.domain.repository.MatchRepository;
import org.ctc.domain.repository.RaceLineupRepository;
import org.ctc.domain.repository.RaceRepository;
import org.ctc.domain.repository.SeasonPhaseRepository;
import org.ctc.domain.repository.SeasonRepository;
import org.ctc.testsupport.CtcDevSpringBootContext;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mutates score sources through the real services and reads the stored aggregate back from the
 * database after clearing the persistence context.
 */
@CtcDevSpringBootContext
@Tag("integration")
@Transactional
class ScoreReaggregationIT {

	@Autowired private RaceService raceService;
	@Autowired private DriverMergeService driverMergeService;
	@Autowired private RaceLineupService raceLineupService;
	@Autowired private MatchService matchService;
	@Autowired private TestHelper testHelper;
	@Autowired private SeasonRepository seasonRepository;
	@Autowired private SeasonPhaseRepository seasonPhaseRepository;
	@Autowired private MatchRepository matchRepository;
	@Autowired private RaceRepository raceRepository;
	@Autowired private RaceLineupRepository raceLineupRepository;
	@Autowired private EntityManager entityManager;

	private final String id = UUID.randomUUID().toString().substring(0, 8);
	private Team home;
	private Team away;
	private Driver homeDriver;
	private Driver awayDriver;
	private Match match;

	@Test
	void givenTheOnlyScoredLeg_whenDeleted_thenTheMatchIsUnplayedAgain() {
		// given
		var leg = createMatchWithLegs(1).getFirst();
		score(leg);
		assertThat(stored().getHomeScore()).as("fixture").isPositive();

		// when
		raceService.deleteRace(leg.getId());

		// then
		assertThat(stored().getHomeScore()).as("home score").isNull();
		assertThat(stored().getAwayScore()).as("away score").isNull();
	}

	@Test
	void givenTwoScoredLegs_whenOneIsDeleted_thenTheMatchKeepsOnlyTheSurvivingLeg() {
		// given
		var legs = createMatchWithLegs(2);
		score(legs.get(0));
		var afterFirstLeg = List.of(stored().getHomeScore(), stored().getAwayScore());
		score(legs.get(1));

		// when
		raceService.deleteRace(legs.get(1).getId());

		// then
		assertThat(List.of(stored().getHomeScore(), stored().getAwayScore()))
				.as("home and away after deleting leg 2").isEqualTo(afterFirstLeg);
	}

	@Test
	void givenAllResultsCleared_whenSaved_thenTheMatchIsUnplayedRatherThanADraw() {
		// given
		var leg = createMatchWithLegs(1).getFirst();
		score(leg);

		// when
		raceService.saveResults(leg.getId(), List.of());

		// then
		assertThat(stored().getHomeScore()).as("home score").isNull();
		assertThat(stored().getAwayScore()).as("away score").isNull();
	}

	@Test
	void givenMergedDriversWithResultsInTheSameRace_whenMerged_thenTheDroppedResultLeavesTheAggregate() {
		// given
		var leg = createMatchWithLegs(1).getFirst();
		score(leg);
		var homeBefore = stored().getHomeScore();

		// when
		driverMergeService.merge(awayDriver.getId(), homeDriver.getId());

		// then
		assertThat(stored().getHomeScore()).as("home score").isEqualTo(homeBefore);
		assertThat(stored().getAwayScore()).as("the away driver's dropped result no longer counts").isZero();
	}

	@Test
	void givenRosterDriverMovedToTheOtherTeam_whenLineupSaved_thenHisPointsMoveWithHim() {
		// given
		var leg = createMatchWithLegs(1).getFirst();
		score(leg);
		var total = stored().getHomeScore() + stored().getAwayScore();

		// when
		raceLineupService.saveLineup(leg.getId(), Map.of(homeDriver.getId(), away.getId(), awayDriver.getId(), away.getId()));

		// then
		assertThat(stored().getHomeScore()).as("home score").isZero();
		assertThat(stored().getAwayScore()).as("away score").isEqualTo(total);
	}

	private List<Race> createMatchWithLegs(int legs) {
		Season season = testHelper.createSeason("Test_Reagg_" + id);
		home = testHelper.createTeam("Test Reagg Home " + id, "Test_RGH_" + id);
		away = testHelper.createTeam("Test Reagg Away " + id, "Test_RGA_" + id);
		season.addTeam(home);
		season.addTeam(away);
		seasonRepository.save(season);
		var regular = seasonPhaseRepository.findById(season.getPhases().getFirst().getId()).orElseThrow();
		regular.setLegs(legs);
		Matchday matchday = testHelper.createMatchdayInRegularPhase(season, "Test_Reagg MD " + id, 1);
		homeDriver = testHelper.createDriver("Test_Reagg_" + id + "_H", "Test Reagg Home Driver");
		awayDriver = testHelper.createDriver("Test_Reagg_" + id + "_A", "Test Reagg Away Driver");
		testHelper.createSeasonDriver(season, homeDriver, home);
		testHelper.createSeasonDriver(season, awayDriver, away);
		match = matchService.createMatchWithLegs(matchday, home, away, false);
		return raceRepository.findByMatchId(match.getId()).stream()
				.sorted(Comparator.comparing(Race::hasTeamOverrides))
				.toList();
	}

	private void score(Race leg) {
		var managed = raceRepository.findById(leg.getId()).orElseThrow();
		raceLineupRepository.save(new RaceLineup(managed, homeDriver, home));
		raceLineupRepository.save(new RaceLineup(managed, awayDriver, away));
		raceService.saveResults(leg.getId(), List.of(
				new RaceService.RaceResultData(homeDriver.getId(), homeDriver.getPsnId(), null, 1, 1, true),
				new RaceService.RaceResultData(awayDriver.getId(), awayDriver.getPsnId(), null, 2, 2, false)));
		entityManager.flush();
		entityManager.clear();
	}

	private Match stored() {
		entityManager.flush();
		entityManager.clear();
		return matchRepository.findById(match.getId()).orElseThrow();
	}
}
