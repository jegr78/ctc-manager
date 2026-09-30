package org.ctc.domain.service;

import jakarta.persistence.EntityManager;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.ctc.TestHelper;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.model.Driver;
import org.ctc.domain.model.Playoff;
import org.ctc.domain.model.PlayoffMatchup;
import org.ctc.domain.model.Race;
import org.ctc.domain.model.RaceLineup;
import org.ctc.domain.model.Team;
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
 * Drives a four-team bracket through the real services: winner rules, the lock on a decided
 * matchup, reopening with its bracket consequences, and the seeding freeze.
 */
@CtcDevSpringBootContext
@Tag("integration")
@Transactional
class PlayoffDecisionIT {

	private static final String DECIDED = "The playoff matchup is decided. Reopen it before changing its results or lineups";

	@Autowired private PlayoffService playoffService;
	@Autowired private PlayoffSeedingService playoffSeedingService;
	@Autowired private RaceService raceService;
	@Autowired private RaceLineupService raceLineupService;
	@Autowired private DriverMergeService driverMergeService;
	@Autowired private TestHelper testHelper;
	@Autowired private SeasonRepository seasonRepository;
	@Autowired private PlayoffMatchupRepository playoffMatchupRepository;
	@Autowired private RaceRepository raceRepository;
	@Autowired private RaceLineupRepository raceLineupRepository;
	@Autowired private EntityManager entityManager;

	private final String id = UUID.randomUUID().toString().substring(0, 8);
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
	void createSeededFourTeamBracket() {
		var season = testHelper.createSeason("Test_PoDecision_" + id);
		alpha = testHelper.createTeam("Test PoDecision Alpha " + id, "Test_PDA_" + id);
		bravo = testHelper.createTeam("Test PoDecision Bravo " + id, "Test_PDB_" + id);
		charlie = testHelper.createTeam("Test PoDecision Charlie " + id, "Test_PDC_" + id);
		delta = testHelper.createTeam("Test PoDecision Delta " + id, "Test_PDD_" + id);
		List.of(alpha, bravo, charlie, delta).forEach(season::addTeam);
		seasonRepository.save(season);
		playoff = playoffService.createPlayoff(season.getId(), "Test PoDecision " + id, 4);
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
		playoffService.setRoundLegs(semi.getRound().getId(), 2);
		alphaDriver = testHelper.createDriver("Test_PoDecision_" + id + "_A", "Test PoDecision Alpha Driver");
		bravoDriver = testHelper.createDriver("Test_PoDecision_" + id + "_B", "Test PoDecision Bravo Driver");
		testHelper.createSeasonDriver(season, alphaDriver, alpha);
		testHelper.createSeasonDriver(season, bravoDriver, bravo);
		entityManager.flush();
	}

	@Test
	void givenALegWithoutResults_whenWinnerDetermined_thenRejected() {
		// given
		score(playoffService.addRaceToMatchup(semi.getId(), null, null, null), alphaDriver, bravoDriver);
		playoffService.addRaceToMatchup(semi.getId(), null, null, null);

		// when / then
		assertThatThrownBy(() -> playoffService.determineWinner(semi.getId()))
				.isInstanceOf(IllegalStateException.class)
				.hasMessage("Every scheduled leg needs results before the winner is determined");
		assertThat(stored(semi).getWinner()).as("winner").isNull();
	}

	@Test
	void givenAllLegsScored_whenWinnerDetermined_thenTheWinnerAdvancesAndTheDecisionIsRecorded() {
		// given
		score(playoffService.addRaceToMatchup(semi.getId(), null, null, null), alphaDriver, bravoDriver);

		// when
		playoffService.determineWinner(semi.getId());

		// then
		assertThat(stored(semi).getWinner().getId()).as("winner").isEqualTo(alpha.getId());
		assertThat(stored(finale).getTeam1().getId()).as("advanced into the final").isEqualTo(alpha.getId());
		assertThat(stored(semi).getDecisionHistory()).as("history").contains("DECIDED " + alpha.getShortName());
	}

	@Test
	void givenDecidedMatchup_whenAWinnerIsSetAgain_thenRejected() {
		// given
		decideSemiForAlpha();

		// when / then
		assertThatThrownBy(() -> playoffService.determineWinner(semi.getId()))
				.isInstanceOf(IllegalStateException.class)
				.hasMessage("The matchup is already decided. Reopen it first");
		assertThatThrownBy(() -> playoffService.setWinnerManually(semi.getId(), bravo.getId(), "Protest upheld"))
				.isInstanceOf(IllegalStateException.class)
				.hasMessage("The matchup is already decided. Reopen it first");
		assertThat(stored(semi).getWinner().getId()).as("winner").isEqualTo(alpha.getId());
	}

	@Test
	void givenDecidedMatchup_whenResultsChangeClearOrTheLegIsDeleted_thenRejected() {
		// given
		var leg = decideSemiForAlpha();

		// when / then
		assertThatThrownBy(() -> raceService.saveResults(leg.getId(), List.of(
				new RaceService.RaceResultData(bravoDriver.getId(), bravoDriver.getPsnId(), null, 1, 1, true))))
				.isInstanceOf(BusinessRuleException.class).hasMessage(DECIDED);
		assertThatThrownBy(() -> raceService.saveResults(leg.getId(), List.of()))
				.isInstanceOf(BusinessRuleException.class).hasMessage(DECIDED);
		assertThatThrownBy(() -> raceService.deleteRace(leg.getId()))
				.isInstanceOf(BusinessRuleException.class).hasMessage(DECIDED);
		assertThatThrownBy(() -> raceLineupService.saveLineup(leg.getId(), Map.of(alphaDriver.getId(), bravo.getId())))
				.isInstanceOf(BusinessRuleException.class).hasMessage(DECIDED);
		assertThatThrownBy(() -> playoffService.addRaceToMatchup(semi.getId(), null, null, null))
				.isInstanceOf(IllegalStateException.class).hasMessage("The matchup is already decided. Reopen it first");
	}

	@Test
	void givenDecidedMatchup_whenAMergeWouldDropAResult_thenRejected() {
		// given
		decideSemiForAlpha();

		// when / then
		assertThatThrownBy(() -> driverMergeService.merge(bravoDriver.getId(), alphaDriver.getId()))
				.isInstanceOf(BusinessRuleException.class).hasMessage(DECIDED);
	}

	@Test
	void givenWinnerContraryToTheScores_whenSetWithoutReason_thenRejectedAndWithReasonAccepted() {
		// given
		score(playoffService.addRaceToMatchup(semi.getId(), null, null, null), alphaDriver, bravoDriver);

		// when / then
		assertThatThrownBy(() -> playoffService.setWinnerManually(semi.getId(), bravo.getId(), " "))
				.isInstanceOf(IllegalStateException.class)
				.hasMessage("A winner that is not the points leader of all scheduled legs needs a reason");
		playoffService.setWinnerManually(semi.getId(), bravo.getId(), "Alpha disqualified for an illegal car");
		var stored = stored(semi);
		assertThat(stored.getWinner().getId()).as("winner").isEqualTo(bravo.getId());
		assertThat(stored.getDecisionReason()).as("reason").isEqualTo("Alpha disqualified for an illegal car");
		assertThat(stored.getHomeScore()).as("race points are preserved").isGreaterThan(stored.getAwayScore());
	}

	@Test
	void givenAnUnscoredLeg_whenAWinnerIsSetWithoutReason_thenRejected() {
		// given
		score(playoffService.addRaceToMatchup(semi.getId(), null, null, null), alphaDriver, bravoDriver);
		playoffService.addRaceToMatchup(semi.getId(), null, null, null);

		// when / then
		assertThatThrownBy(() -> playoffService.setWinnerManually(semi.getId(), alpha.getId(), null))
				.isInstanceOf(IllegalStateException.class)
				.hasMessage("A winner that is not the points leader of all scheduled legs needs a reason");
		playoffService.setWinnerManually(semi.getId(), alpha.getId(), "Bravo withdrew before leg 2");
		assertThat(stored(semi).getWinner().getId()).as("early decision with reason").isEqualTo(alpha.getId());
	}

	@Test
	void givenDecidedMatchupWithAnUnplayedSuccessor_whenReopened_thenAdvancementAndSuccessorLineupsAreRevoked() {
		// given
		decideSemiForAlpha();
		otherSemiDecidedForCharlie();
		var finalLeg = playoffService.addRaceToMatchup(finale.getId(), null, null, null);
		raceLineupRepository.save(new RaceLineup(raceRepository.findById(finalLeg.getId()).orElseThrow(), alphaDriver, alpha));

		// when
		playoffService.reopen(semi.getId(), "Scoring error in leg 1");

		// then
		var reopened = stored(semi);
		assertThat(reopened.getWinner()).as("winner").isNull();
		assertThat(reopened.getDecisionHistory()).as("history keeps the previous decision")
				.contains("DECIDED " + alpha.getShortName())
				.contains("REOPENED " + alpha.getShortName())
				.contains("Scoring error in leg 1");
		assertThat(raceRepository.findByPlayoffMatchupId(semi.getId()).getFirst().getResults())
				.as("the matchup's results are kept").hasSize(2);
		var successor = stored(finale);
		assertThat(successor.getTeam1()).as("obsolete advancement").isNull();
		assertThat(successor.getTeam2().getId()).as("the other semi's advancement stays").isEqualTo(charlie.getId());
		assertThat(raceLineupRepository.findByRaceId(finalLeg.getId())).as("lineups of the revoked team").isEmpty();
		assertThat(raceRepository.findById(finalLeg.getId())).as("the successor's schedule stays").isPresent();
	}

	@Test
	void givenSuccessorWithResults_whenReopened_thenRejected() {
		// given
		decideSemiForAlpha();
		otherSemiDecidedForCharlie();
		var charlieDriver = testHelper.createDriver("Test_PoDecision_" + id + "_C", "Test PoDecision Charlie Driver");
		score(playoffService.addRaceToMatchup(finale.getId(), null, null, null), alphaDriver, charlieDriver);

		// when / then
		assertThatThrownBy(() -> playoffService.reopen(semi.getId(), "Scoring error in leg 1"))
				.isInstanceOf(IllegalStateException.class)
				.hasMessage("A later matchup already has results or a winner. This needs a bracket correction");
		assertThat(stored(semi).getWinner().getId()).as("winner").isEqualTo(alpha.getId());
	}

	@Test
	void givenDecidedMatchup_whenReopenedWithoutReason_thenRejected() {
		// given
		decideSemiForAlpha();

		// when / then
		assertThatThrownBy(() -> playoffService.reopen(semi.getId(), ""))
				.isInstanceOf(IllegalStateException.class)
				.hasMessage("Reopening a matchup needs a reason");
	}

	@Test
	void givenAMatchupWithResults_whenSeedingChanges_thenRejected() {
		// given
		score(playoffService.addRaceToMatchup(semi.getId(), null, null, null), alphaDriver, bravoDriver);

		// when / then
		assertThatThrownBy(() -> playoffSeedingService.seedTeam(otherSemi.getId(), alpha.getId(), 1))
				.isInstanceOf(IllegalStateException.class)
				.hasMessage("Seeding is frozen once a playoff matchup has results or a winner");
		assertThatThrownBy(() -> playoffSeedingService.autoSeedBracket(playoff.getId()))
				.isInstanceOf(IllegalStateException.class)
				.hasMessage("Seeding is frozen once a playoff matchup has results or a winner");
	}

	private Race decideSemiForAlpha() {
		var leg = playoffService.addRaceToMatchup(semi.getId(), null, null, null);
		score(leg, alphaDriver, bravoDriver);
		playoffService.determineWinner(semi.getId());
		entityManager.flush();
		entityManager.clear();
		return leg;
	}

	private void otherSemiDecidedForCharlie() {
		playoffService.addRaceToMatchup(otherSemi.getId(), null, null, null);
		playoffService.setWinnerManually(otherSemi.getId(), charlie.getId(), "Delta withdrew");
		entityManager.flush();
		entityManager.clear();
	}

	private void score(Race leg, Driver winnerDriver, Driver loserDriver) {
		var managed = raceRepository.findById(leg.getId()).orElseThrow();
		var matchup = playoffMatchupRepository.findById(managed.getPlayoffMatchup().getId()).orElseThrow();
		raceLineupRepository.save(new RaceLineup(managed, winnerDriver, matchup.getTeam1()));
		raceLineupRepository.save(new RaceLineup(managed, loserDriver, matchup.getTeam2()));
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
