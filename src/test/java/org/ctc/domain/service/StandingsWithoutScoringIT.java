package org.ctc.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.ctc.TestHelper;
import org.ctc.domain.model.Matchday;
import org.ctc.domain.model.PhaseTeam;
import org.ctc.domain.model.PlayoffMatchup;
import org.ctc.domain.model.Season;
import org.ctc.domain.model.SeasonPhase;
import org.ctc.domain.model.Team;
import org.ctc.domain.repository.MatchRepository;
import org.ctc.domain.repository.PhaseTeamRepository;
import org.ctc.domain.repository.PlayoffMatchupRepository;
import org.ctc.domain.repository.SeasonRepository;
import org.ctc.testsupport.CtcDevSpringBootContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/** A phase whose scoring was cleared keeps its results: standings count games but award no match points. */
@CtcDevSpringBootContext
@Tag("integration")
@Transactional
class StandingsWithoutScoringIT {

	@Autowired private StandingsService standingsService;
	@Autowired private SeasonPhaseService seasonPhaseService;
	@Autowired private PlayoffService playoffService;
	@Autowired private TestHelper testHelper;
	@Autowired private SeasonRepository seasonRepository;
	@Autowired private MatchRepository matchRepository;
	@Autowired private PhaseTeamRepository phaseTeamRepository;
	@Autowired private PlayoffMatchupRepository playoffMatchupRepository;
	@Autowired private EntityManager entityManager;

	private final String id = UUID.randomUUID().toString().substring(0, 8);
	private Season season;
	private SeasonPhase regular;
	private Team alpha;
	private Team bravo;

	@BeforeEach
	void createPlayedPhaseWithoutScoring() {
		season = testHelper.createSeason("Test_NoScoring_" + id);
		alpha = testHelper.createTeam("Test NoScoring Alpha " + id, "Test_NSA_" + id);
		bravo = testHelper.createTeam("Test NoScoring Bravo " + id, "Test_NSB_" + id);
		List.of(alpha, bravo).forEach(season::addTeam);
		season = seasonRepository.save(season);
		regular = seasonPhaseService.findRegularPhase(season.getId());
		List.of(alpha, bravo).forEach(team -> phaseTeamRepository.save(new PhaseTeam(regular, team)));
		Matchday first = testHelper.createMatchdayInRegularPhase(season, "Test_NoScoring MD1 " + id, 1);
		Matchday second = testHelper.createMatchdayInRegularPhase(season, "Test_NoScoring MD2 " + id, 2);
		score(testHelper.createMatch(first, alpha, bravo), 30, 10);
		score(testHelper.createMatch(second, bravo, alpha), 20, 20);
	}

	@Test
	void givenPhaseWithoutMatchScoring_whenStandingsCalculated_thenGamesCountWithoutMatchPoints() {
		// given
		clearScoring(regular);

		// when
		var standings = standingsService.calculateStandings(regular.getId(), null);

		// then
		assertThat(standings).as("standings without match scoring")
				.extracting(s -> s.getTeam().getShortName() + " P" + s.getPlayed() + " W" + s.getWins() + " D" + s.getDraws()
						+ " L" + s.getLosses() + " pts" + s.getPoints() + " for" + s.getPointsFor())
				.containsExactly(alpha.getShortName() + " P2 W1 D1 L0 pts0 for50", bravo.getShortName() + " P2 W0 D1 L1 pts0 for30");
	}

	@Test
	void givenPlayoffPhaseWithoutScoring_whenAlltimeStandingsCalculated_thenTheDecidedMatchupCountsWithoutMatchPoints() {
		// given
		var playoff = playoffService.createPlayoff(season.getId(), "Test_NoScoring Playoff " + id, 2);
		PlayoffMatchup finale = playoffMatchupRepository.findByRoundPlayoffId(playoff.getId()).stream()
				.min(Comparator.comparing(PlayoffMatchup::getBracketPosition)).orElseThrow();
		finale.setTeam1(alpha);
		finale.setTeam2(bravo);
		finale.setWinner(alpha);
		finale.setHomeScore(25);
		finale.setAwayScore(15);
		clearScoring(playoff.getPhase());
		clearScoring(regular);

		// when
		var standings = standingsService.calculateAlltimeStandings(List.of(seasonRepository.findById(season.getId()).orElseThrow()));

		// then
		assertThat(standings).as("alltime standings without scoring")
				.filteredOn(s -> s.getTeam().getId().equals(alpha.getId()))
				.singleElement().satisfies(s -> {
					assertThat(s.getWins()).as("wins of %s", alpha.getShortName()).isEqualTo(2);
					assertThat(s.getPoints()).as("match points of %s", alpha.getShortName()).isZero();
				});
	}

	private void score(org.ctc.domain.model.Match match, int home, int away) {
		match.setHomeScore(home);
		match.setAwayScore(away);
		matchRepository.save(match);
	}

	private void clearScoring(SeasonPhase phase) {
		var managed = entityManager.find(SeasonPhase.class, phase.getId());
		managed.setMatchScoring(null);
		managed.setRaceScoring(null);
		entityManager.flush();
		entityManager.clear();
	}
}
