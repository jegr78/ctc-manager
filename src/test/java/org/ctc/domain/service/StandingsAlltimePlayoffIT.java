package org.ctc.domain.service;

import jakarta.persistence.EntityManager;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.ctc.TestHelper;
import org.ctc.domain.model.Driver;
import org.ctc.domain.model.MatchScoring;
import org.ctc.domain.model.PhaseTeam;
import org.ctc.domain.model.Playoff;
import org.ctc.domain.model.PlayoffMatchup;
import org.ctc.domain.model.Race;
import org.ctc.domain.model.RaceLineup;
import org.ctc.domain.model.Season;
import org.ctc.domain.model.Team;
import org.ctc.domain.repository.MatchRepository;
import org.ctc.domain.repository.MatchScoringRepository;
import org.ctc.domain.repository.PhaseTeamRepository;
import org.ctc.domain.repository.PlayoffMatchupRepository;
import org.ctc.domain.repository.RaceLineupRepository;
import org.ctc.domain.repository.RaceRepository;
import org.ctc.domain.repository.SeasonRepository;
import org.ctc.testsupport.CtcDevSpringBootContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Drives real playoff matchups to their outcomes and reads the Alltime standings back. The playoff
 * phase awards 5 points for a win and 1 for a loss, the regular phase 3 and 0.
 */
@CtcDevSpringBootContext
@Tag("integration")
@Transactional
class StandingsAlltimePlayoffIT {

	@Autowired private StandingsService standingsService;
	@Autowired private PlayoffService playoffService;
	@Autowired private RaceService raceService;
	@Autowired private SeasonPhaseService seasonPhaseService;
	@Autowired private TestHelper testHelper;
	@Autowired private SeasonRepository seasonRepository;
	@Autowired private PlayoffMatchupRepository playoffMatchupRepository;
	@Autowired private MatchScoringRepository matchScoringRepository;
	@Autowired private MatchRepository matchRepository;
	@Autowired private PhaseTeamRepository phaseTeamRepository;
	@Autowired private RaceRepository raceRepository;
	@Autowired private RaceLineupRepository raceLineupRepository;
	@Autowired private EntityManager entityManager;

	private final String id = UUID.randomUUID().toString().substring(0, 8);
	private Season season;
	private Playoff playoff;
	private Team alpha;
	private Team bravo;
	private Team charlie;
	private Team delta;
	private Driver alphaDriver;
	private Driver bravoDriver;
	private PlayoffMatchup semi;
	private PlayoffMatchup otherSemi;
	private PlayoffMatchup finale;

	@BeforeEach
	void createFourTeamBracket() {
		season = testHelper.createSeason("Test_AlltimePo_" + id);
		alpha = testHelper.createTeam("Test AlltimePo Alpha " + id, "Test_APA_" + id);
		bravo = testHelper.createTeam("Test AlltimePo Bravo " + id, "Test_APB_" + id);
		charlie = testHelper.createTeam("Test AlltimePo Charlie " + id, "Test_APC_" + id);
		delta = testHelper.createTeam("Test AlltimePo Delta " + id, "Test_APD_" + id);
		List.of(alpha, bravo, charlie, delta).forEach(season::addTeam);
		seasonRepository.save(season);
		playoff = playoffService.createPlayoff(season.getId(), "Test AlltimePo " + id, 4);
		playoff.getPhase().setMatchScoring(matchScoringRepository.save(new MatchScoring("Test AlltimePo " + id, 5, 2, 1)));
		var matchups = playoffMatchupRepository.findByRoundPlayoffId(playoff.getId()).stream()
				.sorted(Comparator.comparing((PlayoffMatchup m) -> m.getRound().getRoundIndex())
						.thenComparing(PlayoffMatchup::getBracketPosition))
				.toList();
		semi = matchups.get(0);
		otherSemi = matchups.get(1);
		finale = matchups.get(2);
		semi.setTeam1(alpha);
		semi.setTeam2(bravo);
		otherSemi.setTeam1(charlie);
		otherSemi.setTeam2(delta);
		alphaDriver = testHelper.createDriver("Test_AlltimePo_" + id + "_A", "Test AlltimePo Alpha Driver");
		bravoDriver = testHelper.createDriver("Test_AlltimePo_" + id + "_B", "Test AlltimePo Bravo Driver");
		testHelper.createSeasonDriver(season, alphaDriver, alpha);
		testHelper.createSeasonDriver(season, bravoDriver, bravo);
		entityManager.flush();
	}

	@Test
	void givenScoredButUndecidedMatchup_whenAlltimeCalculated_thenItContributesNothing() {
		// given
		score(playoffService.addRaceToMatchup(semi.getId(), null, null, null), alphaDriver, bravoDriver);

		// when
		var alltime = alltime();

		// then
		assertThat(alltime).as("a running matchup is no Alltime game").isEmpty();
	}

	@Test
	void givenTwoLegMatchupDecidedByPoints_whenAlltimeCalculated_thenItIsOneGameWithTheSummedLegPoints() {
		// given
		playoffService.setRoundLegs(semi.getRound().getId(), 2);
		score(playoffService.addRaceToMatchup(semi.getId(), null, null, null), alphaDriver, bravoDriver);
		score(playoffService.addRaceToMatchup(semi.getId(), null, null, null), alphaDriver, bravoDriver);
		playoffService.determineWinner(semi.getId());

		// when
		var alltime = alltime();

		// then
		var winner = standingOf(alltime, alpha);
		assertThat(winner.getPlayed()).as("one game for both legs").isEqualTo(1);
		assertThat(winner.getWins()).as("wins").isEqualTo(1);
		assertThat(winner.getPoints()).as("playoff win points").isEqualTo(5);
		assertThat(winner.getPointsFor()).as("two legs of 20 race + 3 quali + 2 fastest lap").isEqualTo(50);
		assertThat(winner.getPointsAgainst()).as("two legs of 17 race + 2 quali").isEqualTo(38);
		var loser = standingOf(alltime, bravo);
		assertThat(loser.getLosses()).as("losses").isEqualTo(1);
		assertThat(loser.getPoints()).as("playoff loss points").isEqualTo(1);
		assertThat(loser.getPointsFor()).as("loser points for").isEqualTo(38);
	}

	@Test
	void givenTiedMatchupWithAManualWinner_whenAlltimeCalculated_thenTheDeclaredWinnerTakesTheGame() {
		// given
		playoffService.setRoundLegs(semi.getRound().getId(), 2);
		score(playoffService.addRaceToMatchup(semi.getId(), null, null, null), alphaDriver, bravoDriver);
		score(playoffService.addRaceToMatchup(semi.getId(), null, null, null), bravoDriver, alphaDriver);
		playoffService.setWinnerManually(semi.getId(), bravo.getId(), "Test_AlltimePo tiebreak");

		// when
		var alltime = alltime();

		// then
		var winner = standingOf(alltime, bravo);
		assertThat(winner.getWins()).as("the declared winner wins").isEqualTo(1);
		assertThat(winner.getPoints()).as("playoff win points").isEqualTo(5);
		assertThat(winner.getPointsFor()).as("actual race points, 25 + 19").isEqualTo(44);
		assertThat(winner.getPointsAgainst()).as("actual race points against").isEqualTo(44);
		assertThat(standingOf(alltime, alpha).getLosses()).as("the other team loses").isEqualTo(1);
	}

	@Test
	void givenMatchupDecidedAsABye_whenAlltimeCalculated_thenItIsAWinWithoutRacePoints() {
		// given
		semi.setTeam2(null);
		entityManager.flush();
		playoffService.declareBye(semi.getId());

		// when
		var alltime = alltime();

		// then
		assertThat(alltime).as("only the team on the bye appears").hasSize(1);
		var standing = standingOf(alltime, alpha);
		assertThat(standing.getWins()).as("wins").isEqualTo(1);
		assertThat(standing.getPoints()).as("playoff win points").isEqualTo(5);
		assertThat(standing.getPointsFor()).as("points for").isZero();
		assertThat(standing.getPointsAgainst()).as("points against").isZero();
		assertThat(stored(semi).isBye()).as("the matchup is marked as a bye").isTrue();
		assertThat(stored(finale).getTeam1().getId()).as("the team advances").isEqualTo(alpha.getId());
	}

	@Test
	void givenMatchupDecidedByWalkover_whenAlltimeCalculated_thenTheWalkoverScoreCountsOnce() {
		// given
		playoffService.setRoundLegs(otherSemi.getRound().getId(), 3);
		playoffService.declareWalkover(otherSemi.getId(), delta.getId(), "Test_AlltimePo no show");

		// when
		var alltime = alltime();

		// then
		var winner = standingOf(alltime, charlie);
		assertThat(winner.getPoints()).as("playoff win points").isEqualTo(5);
		assertThat(winner.getPointsFor()).as("top six race points 81 + quali 6 + fastest lap 2, once for three legs")
				.isEqualTo(89);
		var forfeiter = standingOf(alltime, delta);
		assertThat(forfeiter.getLosses()).as("losses").isEqualTo(1);
		assertThat(forfeiter.getPoints()).as("no match points for the forfeiting team").isZero();
		assertThat(forfeiter.getPointsAgainst()).as("walkover score against").isEqualTo(89);
		assertThat(forfeiter.isHasWalkover()).as("walkover marker").isTrue();
		assertThat(stored(otherSemi).getWinner().getId()).as("the opponent wins").isEqualTo(charlie.getId());
	}

	@Test
	void givenRegularWinAndPlayoffWin_whenAlltimeCalculated_thenBothPhasesAddUp() {
		// given
		var regular = seasonPhaseService.findRegularPhase(season.getId());
		phaseTeamRepository.save(new PhaseTeam(regular, alpha));
		phaseTeamRepository.save(new PhaseTeam(regular, bravo));
		var match = testHelper.createMatch(testHelper.createMatchdayInRegularPhase(season, "Test_AlltimePo MD " + id, 1),
				alpha, bravo);
		match.setHomeScore(30);
		match.setAwayScore(10);
		matchRepository.save(match);
		score(playoffService.addRaceToMatchup(semi.getId(), null, null, null), alphaDriver, bravoDriver);
		playoffService.determineWinner(semi.getId());

		// when
		var standing = standingOf(alltime(), alpha);

		// then
		assertThat(standing.getPlayed()).as("one regular and one playoff game").isEqualTo(2);
		assertThat(standing.getPoints()).as("regular 3 + playoff 5").isEqualTo(8);
		assertThat(standing.getPointsFor()).as("regular 30 + playoff 25").isEqualTo(55);
	}

	@Test
	void givenOpenSlotFedByAnEarlierMatchup_whenByeDeclared_thenRejected() {
		// given
		playoffService.declareWalkover(semi.getId(), bravo.getId(), null);

		// when / then
		assertThatThrownBy(() -> playoffService.declareBye(finale.getId()))
				.isInstanceOf(IllegalStateException.class)
				.hasMessage("The open slot waits for the winner of an earlier matchup");
		assertThat(stored(finale).getWinner()).as("finale winner").isNull();
	}

	@Test
	void givenScoredLeg_whenWalkoverDeclared_thenRejected() {
		// given
		score(playoffService.addRaceToMatchup(semi.getId(), null, null, null), alphaDriver, bravoDriver);

		// when / then
		assertThatThrownBy(() -> playoffService.declareWalkover(semi.getId(), bravo.getId(), null))
				.isInstanceOf(IllegalStateException.class)
				.hasMessage("A walkover needs a matchup without results");
		assertThat(stored(semi).getWalkoverTeam()).as("walkover team").isNull();
	}

	@Test
	void givenWalkover_whenReopened_thenTheWalkoverIsCleared() {
		// given
		playoffService.declareWalkover(semi.getId(), bravo.getId(), null);

		// when
		playoffService.reopen(semi.getId(), "Test_AlltimePo wrong team");

		// then
		var reopened = stored(semi);
		assertThat(reopened.getWalkoverTeam()).as("walkover team").isNull();
		assertThat(reopened.getWinner()).as("winner").isNull();
		assertThat(alltime()).as("the reopened matchup leaves Alltime").isEmpty();
	}

	private List<StandingsService.TeamStanding> alltime() {
		entityManager.flush();
		entityManager.clear();
		return standingsService.calculateAlltimeStandings(List.of(seasonRepository.findById(season.getId()).orElseThrow()));
	}

	private static StandingsService.TeamStanding standingOf(List<StandingsService.TeamStanding> alltime, Team team) {
		return alltime.stream()
				.filter(standing -> standing.getTeam().getId().equals(team.getId()))
				.findFirst()
				.orElseThrow(() -> new AssertionError("no Alltime standing for " + team.getShortName()));
	}

	private void score(Race leg, Driver winnerDriver, Driver loserDriver) {
		var managed = raceRepository.findById(leg.getId()).orElseThrow();
		var matchup = playoffMatchupRepository.findById(managed.getPlayoffMatchup().getId()).orElseThrow();
		raceLineupRepository.save(new RaceLineup(managed, alphaDriver, matchup.getTeam1()));
		raceLineupRepository.save(new RaceLineup(managed, bravoDriver, matchup.getTeam2()));
		raceService.saveResults(leg.getId(), List.of(
				new RaceService.RaceResultData(winnerDriver.getId(), winnerDriver.getPsnId(), null, 1, 1, true),
				new RaceService.RaceResultData(loserDriver.getId(), loserDriver.getPsnId(), null, 2, 2, false)));
		entityManager.flush();
		entityManager.clear();
	}

	private PlayoffMatchup stored(PlayoffMatchup matchup) {
		entityManager.flush();
		entityManager.clear();
		return playoffMatchupRepository.findById(matchup.getId()).orElseThrow();
	}
}
