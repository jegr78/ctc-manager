package org.ctc.domain.service;

import org.ctc.domain.model.*;
import org.ctc.domain.repository.PlayoffRepository;
import org.ctc.domain.repository.PlayoffSeedRepository;
import org.ctc.domain.repository.RaceRepository;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlayoffBracketViewServiceTest {

	@Mock
	private PlayoffRepository playoffRepository;
	@Mock
	private RaceRepository raceRepository;
	@Mock
	private PlayoffSeedRepository playoffSeedRepository;
	@Mock
	private ScoringService scoringService;

	@InjectMocks
	private PlayoffBracketViewService playoffBracketViewService;

	private Season createSeason(String name) {
		var season = new Season(name);
		season.setId(UUID.randomUUID());
		return season;
	}


	private Team createTeam(String name) {
		var team = new Team(name, name);
		team.setId(UUID.randomUUID());
		return team;
	}

	private Driver createDriver(String psnId) {
		var driver = new Driver(psnId, psnId);
		driver.setId(UUID.randomUUID());
		return driver;
	}

	private Race createRace(PlayoffMatchup matchup) {
		var race = new Race();
		race.setId(UUID.randomUUID());
		race.setPlayoffMatchup(matchup);
		race.setResults(new ArrayList<>());
		return race;
	}

	private RaceResult createResult(Driver driver, int points) {
		var result = new RaceResult();
		result.setDriver(driver);
		result.setPointsTotal(points);
		return result;
	}

	@Nested
	class GetBracketView {

		@Test
		void givenPlayoffWithOneRoundAndOneMatchup_whenGetBracketView_thenReturnsCorrectView() {
			// given
			var playoffId = UUID.randomUUID();
			var season = createSeason("Test Season");
			// Playoff is bound to a SeasonPhase, not the Season directly.
			var playoff = PhaseTestFixtures.playoffForSeason(season,"Test Playoff");
			playoff.setId(playoffId);

			var round = new PlayoffRound(playoff, "Final", 0);
			round.setId(UUID.randomUUID());

			var matchup = new PlayoffMatchup(round, 0);
			matchup.setId(UUID.randomUUID());
			round.getMatchups().add(matchup);
			playoff.getRounds().add(round);

			when(playoffRepository.findById(playoffId)).thenReturn(Optional.of(playoff));
			when(raceRepository.findByPlayoffMatchupRoundPlayoffId(playoffId)).thenReturn(List.of());
			when(playoffSeedRepository.findByPlayoffId(playoffId)).thenReturn(List.of());

			// when
			var view = playoffBracketViewService.getBracketView(playoffId);

			// then
			assertEquals("Test Playoff", view.name());
			assertEquals(playoffId, view.playoffId());
			assertEquals(1, view.rounds().size());
			assertEquals("Final", view.rounds().get(0).label());
			assertEquals(1, view.rounds().get(0).matchups().size());
		}

		@Test
		void givenMatchupWithTwoLegs_whenGetBracketView_thenAggregatesPointsAcrossLegs() {
			// given
			var playoffId = UUID.randomUUID();
			var season = createSeason("Test Season");
			// Playoff is bound to a SeasonPhase, not the Season directly.
			var playoff = PhaseTestFixtures.playoffForSeason(season,"Points Test");
			playoff.setId(playoffId);

			var team1 = createTeam("T1");
			var team2 = createTeam("T2");

			var round = new PlayoffRound(playoff, "Final", 0);
			round.setId(UUID.randomUUID());

			var matchup = new PlayoffMatchup(round, 0);
			matchup.setId(UUID.randomUUID());
			matchup.setTeam1(team1);
			matchup.setTeam2(team2);
			round.getMatchups().add(matchup);
			playoff.getRounds().add(round);

			// Two race legs
			var leg1 = createRace(matchup);
			var leg2 = createRace(matchup);
			var driver1 = createDriver("d1");
			var r1 = createResult(driver1, 10);
			leg1.setResults(List.of(r1));
			var r2 = createResult(driver1, 15);
			leg2.setResults(List.of(r2));

			when(playoffRepository.findById(playoffId)).thenReturn(Optional.of(playoff));
			when(raceRepository.findByPlayoffMatchupRoundPlayoffId(playoffId))
					.thenReturn(List.of(leg1, leg2));
			when(playoffSeedRepository.findByPlayoffId(playoffId)).thenReturn(List.of());

			// Leg 1: team1=10, team2=0; Leg 2: team1=15, team2=0
			when(scoringService.calculateTeamTotals(leg1.getResults(), leg1.getId(), team1.getId()))
					.thenReturn(new int[]{10, 0});
			when(scoringService.calculateTeamTotals(leg2.getResults(), leg2.getId(), team1.getId()))
					.thenReturn(new int[]{15, 0});

			// when
			var view = playoffBracketViewService.getBracketView(playoffId);

			// then
			var matchupView = view.rounds().get(0).matchups().get(0);
			assertEquals(25, matchupView.team1AggregatePoints());
			assertEquals(0, matchupView.team2AggregatePoints());
			assertEquals(2, matchupView.legs().size());
		}

		@Test
		void givenSeededPlayoff_whenGetBracketView_thenMatchupViewContainsSeedNumbers() {
			// given
			var playoffId = UUID.randomUUID();
			var season = createSeason("Test Season");
			// Playoff is bound to a SeasonPhase, not the Season directly.
			var playoff = PhaseTestFixtures.playoffForSeason(season,"Seed Test");
			playoff.setId(playoffId);

			var team1 = createTeam("T1");
			var team2 = createTeam("T2");

			var round = new PlayoffRound(playoff, "Final", 0);
			round.setId(UUID.randomUUID());

			var matchup = new PlayoffMatchup(round, 0);
			matchup.setId(UUID.randomUUID());
			matchup.setTeam1(team1);
			matchup.setTeam2(team2);
			round.getMatchups().add(matchup);
			playoff.getRounds().add(round);

			var seed1 = new PlayoffSeed(playoff, team1, 1);
			var seed2 = new PlayoffSeed(playoff, team2, 2);

			when(playoffRepository.findById(playoffId)).thenReturn(Optional.of(playoff));
			when(raceRepository.findByPlayoffMatchupRoundPlayoffId(playoffId)).thenReturn(List.of());
			when(playoffSeedRepository.findByPlayoffId(playoffId)).thenReturn(List.of(seed1, seed2));

			// when
			var view = playoffBracketViewService.getBracketView(playoffId);

			// then
			var matchupView = view.rounds().get(0).matchups().get(0);
			assertEquals(Integer.valueOf(1), matchupView.team1Seed());
			assertEquals(Integer.valueOf(2), matchupView.team2Seed());
		}

		@Test
		void givenMatchupWithWinner_whenGetBracketView_thenWinnerFlagsSetCorrectly() {
			// given
			var playoffId = UUID.randomUUID();
			var season = createSeason("Test Season");
			// Playoff is bound to a SeasonPhase, not the Season directly.
			var playoff = PhaseTestFixtures.playoffForSeason(season,"Winner Test");
			playoff.setId(playoffId);

			var team1 = createTeam("T1");
			var team2 = createTeam("T2");

			var round = new PlayoffRound(playoff, "Final", 0);
			round.setId(UUID.randomUUID());

			var matchup = new PlayoffMatchup(round, 0);
			matchup.setId(UUID.randomUUID());
			matchup.setTeam1(team1);
			matchup.setTeam2(team2);
			matchup.setWinner(team1);
			round.getMatchups().add(matchup);
			playoff.getRounds().add(round);

			when(playoffRepository.findById(playoffId)).thenReturn(Optional.of(playoff));
			when(raceRepository.findByPlayoffMatchupRoundPlayoffId(playoffId)).thenReturn(List.of());
			when(playoffSeedRepository.findByPlayoffId(playoffId)).thenReturn(List.of());

			// when
			var view = playoffBracketViewService.getBracketView(playoffId);

			// then
			var matchupView = view.rounds().get(0).matchups().get(0);
			assertTrue(matchupView.team1IsWinner());
			assertFalse(matchupView.team2IsWinner());
		}

		@Test
		void givenNoDoesScoringServiceNotCalledWhenLegsEmpty_whenGetBracketView_thenNoCalculation() {
			// given
			var playoffId = UUID.randomUUID();
			var season = createSeason("Test Season");
			// Playoff is bound to a SeasonPhase, not the Season directly.
			var playoff = PhaseTestFixtures.playoffForSeason(season,"Empty Legs Test");
			playoff.setId(playoffId);

			var round = new PlayoffRound(playoff, "Final", 0);
			round.setId(UUID.randomUUID());

			var matchup = new PlayoffMatchup(round, 0);
			matchup.setId(UUID.randomUUID());
			round.getMatchups().add(matchup);
			playoff.getRounds().add(round);

			when(playoffRepository.findById(playoffId)).thenReturn(Optional.of(playoff));
			when(raceRepository.findByPlayoffMatchupRoundPlayoffId(playoffId)).thenReturn(List.of());
			when(playoffSeedRepository.findByPlayoffId(playoffId)).thenReturn(List.of());

			// when
			playoffBracketViewService.getBracketView(playoffId);

			// then — scoringService.calculateTeamTotals should never be called when no legs
			verify(scoringService, never()).calculateTeamTotals(any(), any(), any());
		}
	}
}
