package org.ctc.domain.service;

import java.time.LocalDate;
import java.util.UUID;
import org.ctc.TestHelper;
import org.ctc.domain.model.Matchday;
import org.ctc.domain.model.PhaseLayout;
import org.ctc.domain.model.PhaseTeam;
import org.ctc.domain.model.PhaseType;
import org.ctc.domain.model.RaceLineup;
import org.ctc.domain.model.RaceResult;
import org.ctc.domain.model.Season;
import org.ctc.domain.model.SeasonFormat;
import org.ctc.domain.model.SeasonPhase;
import org.ctc.domain.model.Team;
import org.ctc.domain.repository.MatchRepository;
import org.ctc.domain.repository.PhaseTeamRepository;
import org.ctc.domain.repository.RaceLineupRepository;
import org.ctc.domain.repository.RaceRepository;
import org.ctc.domain.repository.SeasonRepository;
import org.ctc.domain.repository.SeasonTeamRepository;
import org.ctc.testsupport.CtcDevSpringBootContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@CtcDevSpringBootContext
@Tag("integration")
@Transactional
class TeamReplacementPhaseRosterIT {

	private static final LocalDate REPLACED_AT = LocalDate.of(2026, 6, 1);

	@Autowired private SeasonManagementService seasonManagementService;
	@Autowired private SeasonPhaseService seasonPhaseService;
	@Autowired private StandingsService standingsService;
	@Autowired private MatchdayGeneratorService matchdayGeneratorService;
	@Autowired private ScoringService scoringService;
	@Autowired private TestHelper testHelper;
	@Autowired private SeasonRepository seasonRepository;
	@Autowired private SeasonTeamRepository seasonTeamRepository;
	@Autowired private PhaseTeamRepository phaseTeamRepository;
	@Autowired private MatchRepository matchRepository;
	@Autowired private RaceRepository raceRepository;
	@Autowired private RaceLineupRepository raceLineupRepository;

	private final String id = UUID.randomUUID().toString().substring(0, 8);
	private Season season;
	private SeasonPhase regular;
	private SeasonPhase placement;
	private Team predecessor;
	private Team opponent;
	private Team placementOpponent;
	private Team successor;

	@BeforeEach
	void createScoredRegularPhaseAndUnplayedPlacementPhase() {
		season = testHelper.createSeason("Test_Replace_" + id);
		predecessor = testHelper.createTeam("Test Replace Old " + id, "Test_RO_" + id);
		opponent = testHelper.createTeam("Test Replace Opp " + id, "Test_RX_" + id);
		placementOpponent = testHelper.createTeam("Test Replace Plc " + id, "Test_RY_" + id);
		successor = testHelper.createTeam("Test Replace New " + id, "Test_RN_" + id);
		season.addTeam(predecessor);
		season.addTeam(opponent);
		season.addTeam(placementOpponent);
		season = seasonRepository.save(season);

		regular = season.getPhases().getFirst();
		phaseTeamRepository.save(new PhaseTeam(regular, predecessor));
		phaseTeamRepository.save(new PhaseTeam(regular, opponent));
		placement = seasonPhaseService.create(season.getId(), PhaseType.PLACEMENT, PhaseLayout.LEAGUE, 5,
				"Placement", regular.getRaceScoring(), regular.getMatchScoring(), SeasonFormat.LEAGUE,
				null, null, null, 1, null);
		phaseTeamRepository.save(new PhaseTeam(placement, predecessor));
		phaseTeamRepository.save(new PhaseTeam(placement, placementOpponent));

		Matchday matchday = testHelper.createMatchdayInRegularPhase(season, "Test_Replace MD " + id, 1);
		var match = testHelper.createMatch(matchday, predecessor, opponent);
		var race = testHelper.createRace(matchday, match);
		var driver = testHelper.createDriver("Test_Replace_" + id, "Test Replace Driver");
		testHelper.createSeasonDriver(season, driver, predecessor);
		var result = new RaceResult(race, driver, 1, 1, true);
		scoringService.calculatePoints(result, regular.getRaceScoring());
		race.getResults().add(result);
		raceRepository.saveAndFlush(race);
		raceLineupRepository.save(new RaceLineup(race, driver, predecessor));
		scoringService.aggregateMatchScores(race);
	}

	@Test
	void givenScoredPredecessor_whenReplaced_thenSuccessorHoldsItsStandingAndIsPairedInsteadOfIt() {
		// given
		var before = standingOf(predecessor.getId());

		// when
		seasonManagementService.replaceTeam(season.getId(), predecessor.getId(), successor.getId(), REPLACED_AT);
		matchdayGeneratorService.generate(placement.getId(), null, 1, false);

		// then
		var after = standingOf(successor.getId());
		assertThat(after.getPoints()).as("points move to the successor").isEqualTo(before.getPoints()).isPositive();
		assertThat(after.getPlayed()).as("played").isEqualTo(before.getPlayed());
		assertThat(standingsService.calculateStandings(regular.getId(), null))
				.as("the predecessor has no standing of its own any more")
				.noneMatch(s -> s.getTeam().getId().equals(predecessor.getId()));
		assertThat(phaseTeamRepository.findByPhaseIdAndTeamId(placement.getId(), successor.getId()))
				.as("the successor takes the placement place").isPresent();
		assertThat(matchRepository.findByMatchdayPhaseId(placement.getId()))
				.as("generated placement pairings").isNotEmpty()
				.allSatisfy(m -> assertThat(m.getHomeTeam().getId().equals(successor.getId())
						|| m.getAwayTeam().getId().equals(successor.getId()))
						.as("the successor is paired, not the predecessor").isTrue());
		assertThat(matchRepository.findByMatchdayPhaseId(regular.getId()))
				.as("race history keeps the original team")
				.allSatisfy(m -> assertThat(m.getHomeTeam().getId()).isEqualTo(predecessor.getId()));
	}

	@Test
	void givenSuccessorAlreadyHoldsAPlaceInAnAffectedPhase_whenReplaced_thenRejectedBeforeAnyChange() {
		// given
		season.addTeam(successor);
		seasonRepository.save(season);
		phaseTeamRepository.save(new PhaseTeam(placement, successor));

		// when / then
		assertThatThrownBy(() -> seasonManagementService.replaceTeam(season.getId(), predecessor.getId(),
				successor.getId(), REPLACED_AT))
				.isInstanceOf(IllegalStateException.class)
				.hasMessage(successor.getShortName() + " already holds a place in phase Placement");
		assertThat(seasonTeamRepository.findBySeasonIdAndTeamId(season.getId(), predecessor.getId()).orElseThrow()
				.isReplaced()).isFalse();
	}

	@Test
	void givenSelfReplacement_whenReplaced_thenRejected() {
		assertThatThrownBy(() -> seasonManagementService.replaceTeam(season.getId(), predecessor.getId(),
				predecessor.getId(), REPLACED_AT))
				.isInstanceOf(IllegalStateException.class)
				.hasMessage("A team cannot replace itself");
	}

	@Test
	void givenSuccessorThatWasItselfReplaced_whenReplaced_thenRejectedSoNoCycleCanForm() {
		// given
		seasonManagementService.replaceTeam(season.getId(), opponent.getId(), successor.getId(), REPLACED_AT);

		// when / then
		assertThatThrownBy(() -> seasonManagementService.replaceTeam(season.getId(), successor.getId(),
				opponent.getId(), REPLACED_AT))
				.isInstanceOf(IllegalStateException.class)
				.hasMessage(opponent.getShortName() + " was already replaced and cannot become a successor");
	}

	private StandingsService.TeamStanding standingOf(UUID teamId) {
		return standingsService.calculateStandings(regular.getId(), null).stream()
				.filter(s -> s.getTeam().getId().equals(teamId))
				.findFirst()
				.orElseThrow(() -> new AssertionError("no regular-phase standing for team " + teamId));
	}
}
