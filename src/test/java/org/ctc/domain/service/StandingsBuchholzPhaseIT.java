package org.ctc.domain.service;

import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.UUID;
import org.ctc.TestHelper;
import org.ctc.domain.model.Matchday;
import org.ctc.domain.model.PhaseLayout;
import org.ctc.domain.model.PhaseTeam;
import org.ctc.domain.model.PhaseType;
import org.ctc.domain.model.Season;
import org.ctc.domain.model.SeasonFormat;
import org.ctc.domain.model.SeasonPhase;
import org.ctc.domain.model.Team;
import org.ctc.domain.repository.MatchRepository;
import org.ctc.domain.repository.MatchdayRepository;
import org.ctc.domain.repository.PhaseTeamRepository;
import org.ctc.domain.repository.SeasonRepository;
import org.ctc.testsupport.CtcDevSpringBootContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Plays a regular phase, then a placement match between two of its teams, and checks that the
 * regular phase's Buchholz totals and tiebreak order stay as they were.
 */
@CtcDevSpringBootContext
@Tag("integration")
@Transactional
class StandingsBuchholzPhaseIT {

	@Autowired private StandingsService standingsService;
	@Autowired private SeasonPhaseService seasonPhaseService;
	@Autowired private TestHelper testHelper;
	@Autowired private SeasonRepository seasonRepository;
	@Autowired private MatchRepository matchRepository;
	@Autowired private MatchdayRepository matchdayRepository;
	@Autowired private PhaseTeamRepository phaseTeamRepository;
	@Autowired private EntityManager entityManager;

	private final String id = UUID.randomUUID().toString().substring(0, 8);
	private Season season;
	private SeasonPhase regular;
	private Team alpha;
	private Team bravo;
	private Team charlie;
	private Team delta;

	@BeforeEach
	void playRegularPhase() {
		season = testHelper.createSeason("Test_Buchholz_" + id);
		alpha = testHelper.createTeam("Test Buchholz Alpha " + id, "Test_BHA_" + id);
		bravo = testHelper.createTeam("Test Buchholz Bravo " + id, "Test_BHB_" + id);
		charlie = testHelper.createTeam("Test Buchholz Charlie " + id, "Test_BHC_" + id);
		delta = testHelper.createTeam("Test Buchholz Delta " + id, "Test_BHD_" + id);
		List.of(alpha, bravo, charlie, delta).forEach(season::addTeam);
		seasonRepository.save(season);
		regular = seasonPhaseService.findRegularPhase(season.getId());
		List.of(alpha, bravo, charlie, delta).forEach(team -> phaseTeamRepository.save(new PhaseTeam(regular, team)));
		var matchday = testHelper.createMatchdayInRegularPhase(season, "Test_Buchholz MD " + id, 1);
		play(matchday, alpha, bravo);
		play(matchday, bravo, delta);
		play(matchday, charlie, delta);
	}

	@Test
	void givenRegularPhaseStandings_whenAPlacementMatchIsPlayed_thenTheRegularBuchholzAndOrderStay() {
		// given
		var before = regularStandings();
		assertThat(before).as("regular order before: three teams on 3 points, split by Buchholz")
				.extracting(standing -> standing.getTeam().getShortName())
				.containsExactly(alpha.getShortName(), bravo.getShortName(), charlie.getShortName(), delta.getShortName());
		var placement = seasonPhaseService.create(season.getId(), PhaseType.PLACEMENT, PhaseLayout.LEAGUE, 5,
				"Placement", regular.getRaceScoring(), regular.getMatchScoring(), SeasonFormat.LEAGUE,
				null, null, null, 1, null);
		phaseTeamRepository.save(new PhaseTeam(placement, alpha));
		phaseTeamRepository.save(new PhaseTeam(placement, charlie));

		// when
		play(matchdayRepository.save(new Matchday(placement, "Test_Buchholz PL " + id, 50)), charlie, alpha);

		// then
		var after = regularStandings();
		assertThat(after).as("regular Buchholz after the placement match")
				.extracting(standing -> standing.getTeam().getShortName() + "=" + standing.getBuchholz())
				.containsExactly(alpha.getShortName() + "=3", bravo.getShortName() + "=3",
						charlie.getShortName() + "=0", delta.getShortName() + "=6");
	}

	private List<StandingsService.TeamStanding> regularStandings() {
		entityManager.flush();
		entityManager.clear();
		return standingsService.calculateStandingsWithBuchholz(regular.getId(), null);
	}

	private void play(Matchday matchday, Team winner, Team loser) {
		var match = testHelper.createMatch(matchday, winner, loser);
		match.setHomeScore(30);
		match.setAwayScore(10);
		matchRepository.save(match);
		testHelper.createRace(matchday, match);
	}
}
