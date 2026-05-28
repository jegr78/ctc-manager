package org.ctc.domain.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.ctc.domain.model.*;
import org.ctc.domain.repository.MatchRepository;
import org.ctc.domain.repository.PlayoffMatchupRepository;
import org.ctc.domain.repository.RaceLineupRepository;
import org.ctc.domain.repository.RaceRepository;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScoringServiceTest {

	@Mock
	private RaceLineupRepository raceLineupRepository;

	@Mock
	private RaceRepository raceRepository;

	@Mock
	private MatchRepository matchRepository;

	@Mock
	private PlayoffMatchupRepository playoffMatchupRepository;

	@InjectMocks
	private ScoringService scoringService;

	// Standard scoring preset matching current hardcoded values
	private static RaceScoring standardScoring() {
		return new RaceScoring("CTC Standard", "20,17,14,12,10,8,7,6,5,4,3,2", "3,2,1", 2);
	}

	@Nested
	class CalculatePointsWithScoringTest {

		@Test
		void whenCalculatePoints_thenCorrectTotalPoints() {
			// given
			var scoring = standardScoring();
			var driver = new Driver("panicpotato17", "panicpotato17");
			var result = new RaceResult(new Race(), driver, 1, 1, false);

			// when
			scoringService.calculatePoints(result, scoring);

			// then
			assertEquals(20, result.getPointsRace());
			assertEquals(3, result.getPointsQuali());
			assertEquals(0, result.getPointsFl());
			assertEquals(23, result.getPointsTotal());
		}

		@Test
		void givenFastestLap_whenCalculatePoints_thenFastestLapPointsIncluded() {
			// given
			var scoring = standardScoring();
			var driver = new Driver("P1R_Jake", "P1R_Jake");
			var result = new RaceResult(new Race(), driver, 11, 2, true);

			// when
			scoringService.calculatePoints(result, scoring);

			// then
			assertEquals(3, result.getPointsRace());
			assertEquals(2, result.getPointsQuali());
			assertEquals(2, result.getPointsFl());
			assertEquals(7, result.getPointsTotal());
		}

		@Test
		void givenLegacyScoringWithNoQuali_whenCalculatePoints_thenOnlyRacePointsCounted() {
			// given
			var scoring = new RaceScoring("Legacy", "15,12,10,8,6,4,3,2,1", null, 0);
			var driver = new Driver("driver1", "Driver 1");
			var result = new RaceResult(new Race(), driver, 1, 1, true);

			// when
			scoringService.calculatePoints(result, scoring);

			// then
			assertEquals(15, result.getPointsRace());
			assertEquals(0, result.getPointsQuali()); // no quali points
			assertEquals(0, result.getPointsFl());     // FL disabled
			assertEquals(15, result.getPointsTotal());
		}

		@Test
		void givenPositionBeyondScale_whenCalculatePoints_thenZeroPoints() {
			// given
			var scoring = new RaceScoring("Short", "10,5", "3", 0);
			var driver = new Driver("driver1", "Driver 1");
			var result = new RaceResult(new Race(), driver, 3, 2, false);

			// when
			scoringService.calculatePoints(result, scoring);

			// then
			assertEquals(0, result.getPointsRace());  // position 3 not in scale
			assertEquals(0, result.getPointsQuali());  // quali pos 2 not in scale (only 1 entry)
			assertEquals(0, result.getPointsTotal());
		}

		@Test
		void givenMultipleResults_whenCalculatePointsList_thenEachResultScored() {
			// given
			var scoring = standardScoring();
			var driver1 = new Driver("driver1", "Driver 1");
			var driver2 = new Driver("driver2", "Driver 2");
			var race = new Race();
			var results = List.of(
					new RaceResult(race, driver1, 1, 1, false),
					new RaceResult(race, driver2, 12, 12, false)
			);

			// when
			scoringService.calculatePoints(results, scoring);

			// then
			assertEquals(23, results.get(0).getPointsTotal());
			assertEquals(2, results.get(1).getPointsTotal());
		}
	}

	@Nested
	class TeamTotalTest {

		@Test
		void whenCalculateTeamTotal_thenSumsAllResults() {
			// given
			var race = new Race();
			var results = List.of(
					createResultWithTotal(race, "LEVITIUS", 11),
					createResultWithTotal(race, "panicpotato17", 23),
					createResultWithTotal(race, "Deekuhn", 14),
					createResultWithTotal(race, "LotariRacing", 12),
					createResultWithTotal(race, "Nutcap_1", 8),
					createResultWithTotal(race, "Ghostriderz16173", 2)
			);

			// when / then
			assertEquals(70, scoringService.calculateTeamTotal(results));
		}

		@Test
		void givenEmptyList_whenCalculateTeamTotal_thenReturnsZero() {
			// when / then
			assertEquals(0, scoringService.calculateTeamTotal(List.of()));
		}

		private RaceResult createResultWithTotal(Race race, String psnId, int total) {
			var result = new RaceResult();
			result.setRace(race);
			result.setDriver(new Driver(psnId, psnId));
			result.setPointsTotal(total);
			return result;
		}
	}

	@Nested
	class CalculateTeamTotalsTest {

		@Test
		void givenResultsForTwoTeams_whenCalculateTeamTotals_thenReturnsTwoTotals() {
			// given
			var race = new Race();
			race.setId(UUID.randomUUID());
			var team1 = new Team("Home", "HM");
			team1.setId(UUID.randomUUID());
			var team2 = new Team("Away", "AW");
			team2.setId(UUID.randomUUID());

			var driver1 = new Driver("d1", "Driver 1");
			driver1.setId(UUID.randomUUID());
			var driver2 = new Driver("d2", "Driver 2");
			driver2.setId(UUID.randomUUID());

			var r1 = new RaceResult();
			r1.setDriver(driver1);
			r1.setPointsTotal(20);
			var r2 = new RaceResult();
			r2.setDriver(driver2);
			r2.setPointsTotal(15);

			when(raceLineupRepository.findByRaceIdAndDriverId(race.getId(), driver1.getId()))
					.thenReturn(java.util.Optional.of(new RaceLineup(race, driver1, team1)));
			when(raceLineupRepository.findByRaceIdAndDriverId(race.getId(), driver2.getId()))
					.thenReturn(java.util.Optional.of(new RaceLineup(race, driver2, team2)));

			// when
			int[] totals = scoringService.calculateTeamTotals(List.of(r1, r2), race.getId(), team1.getId());

			// then
			assertEquals(20, totals[0]);
			assertEquals(15, totals[1]);
		}

		@Test
		void givenEmptyResults_whenCalculateTeamTotals_thenReturnsZeroes() {
			// when
			int[] totals = scoringService.calculateTeamTotals(List.of(), UUID.randomUUID(), UUID.randomUUID());

			// then
			assertEquals(0, totals[0]);
			assertEquals(0, totals[1]);
		}
	}

	@Nested
	class AggregateMatchScoresTest {

		@Test
		void givenRaceLineupExists_whenAggregateMatchScores_thenLineupDeterminesTeamAssignment() {
			// given
			var homeTeam = createTeam("Home");
			var awayTeam = createTeam("Away");
			var match = createMatch(homeTeam, awayTeam);
			var race = createRace(match);

			var homeDriver = createDriver("home_d");
			var awayDriver = createDriver("away_d");

			var r1 = createResult(race, homeDriver, 10);
			var r2 = createResult(race, awayDriver, 7);
			race.setResults(List.of(r1, r2));

			// RaceLineup determines team assignment
			when(raceLineupRepository.findByRaceIdAndDriverId(race.getId(), homeDriver.getId()))
					.thenReturn(Optional.of(new RaceLineup(race, homeDriver, homeTeam)));
			when(raceLineupRepository.findByRaceIdAndDriverId(race.getId(), awayDriver.getId()))
					.thenReturn(Optional.of(new RaceLineup(race, awayDriver, awayTeam)));
			when(raceRepository.findByMatchId(match.getId())).thenReturn(List.of(race));

			// when
			scoringService.aggregateMatchScores(race);

			// then
			assertEquals(10, match.getHomeScore());
			assertEquals(7, match.getAwayScore());
		}

		@Test
		void givenNoRaceLineup_whenAggregateMatchScores_thenFallsBackToSeasonDriver() {
			// given
			var homeTeam = createTeam("Home");
			var awayTeam = createTeam("Away");
			var season = new Season("Test");
			season.setId(UUID.randomUUID());
			var match = createMatch(homeTeam, awayTeam);
			// matchday's season is derived from phase; rebind the matchday's
			// phase to this test's `season` so the SeasonDriver fallback (matched on season.id)
			// resolves correctly.
			match.getMatchday().setPhase(PhaseTestFixtures.regularPhase(season, null, null));
			var race = createRace(match);

			var homeDriver = createDriver("home_d");
			var awayDriver = createDriver("away_d");

			// Set up SeasonDriver as fallback
			var homeSd = new SeasonDriver();
			homeSd.setTeam(homeTeam);
			homeSd.setSeason(season);
			homeDriver.setSeasonDrivers(new ArrayList<>(List.of(homeSd)));

			var awaySd = new SeasonDriver();
			awaySd.setTeam(awayTeam);
			awaySd.setSeason(season);
			awayDriver.setSeasonDrivers(new ArrayList<>(List.of(awaySd)));

			var r1 = createResult(race, homeDriver, 15);
			var r2 = createResult(race, awayDriver, 12);
			race.setResults(List.of(r1, r2));

			// No RaceLineup exists — fallback to SeasonDriver
			when(raceLineupRepository.findByRaceIdAndDriverId(any(), any()))
					.thenReturn(Optional.empty());
			when(raceRepository.findByMatchId(match.getId())).thenReturn(List.of(race));
			when(raceRepository.findById(race.getId())).thenReturn(Optional.of(race));

			// when
			scoringService.aggregateMatchScores(race);

			// then
			assertEquals(15, match.getHomeScore());
			assertEquals(12, match.getAwayScore());
		}

		@Test
		void givenSubTeamDriver_whenAggregateMatchScores_thenPointsCountForParentTeam() {
			// given
			var parentTeam = createTeam("Parent");
			var subTeam = createTeam("Sub");
			subTeam.setParentTeam(parentTeam);
			var awayTeam = createTeam("Away");

			// Match is between parent team and away team
			var match = createMatch(parentTeam, awayTeam);
			var race = createRace(match);

			var driver = createDriver("sub_driver");
			var r1 = createResult(race, driver, 20);
			race.setResults(List.of(r1));

			// RaceLineup points to sub-team, but match uses parent team
			when(raceLineupRepository.findByRaceIdAndDriverId(race.getId(), driver.getId()))
					.thenReturn(Optional.of(new RaceLineup(race, driver, subTeam)));
			when(raceRepository.findByMatchId(match.getId())).thenReturn(List.of(race));

			// when
			scoringService.aggregateMatchScores(race);

			// then
			// Driver's points should count for home (parent) team
			assertEquals(20, match.getHomeScore());
			assertEquals(0, match.getAwayScore());
		}

		private Team createTeam(String name) {
			var team = new Team(name, name);
			team.setId(UUID.randomUUID());
			return team;
		}

		private Match createMatch(Team home, Team away) {
			// matchday now derives season via phase; wire a synthetic phase
			// so matchday.getSeason() does not return null in scoring fallback paths.
			var season = new Season("Test");
			season.setId(UUID.randomUUID());
			var matchday = new Matchday();
			matchday.setId(UUID.randomUUID());
			matchday.setPhase(PhaseTestFixtures.regularPhase(season, null, null));
			var match = new Match();
			match.setId(UUID.randomUUID());
			match.setMatchday(matchday);
			match.setHomeTeam(home);
			match.setAwayTeam(away);
			return match;
		}

		private Race createRace(Match match) {
			var race = new Race();
			race.setId(UUID.randomUUID());
			race.setMatch(match);
			race.setMatchday(match.getMatchday());
			return race;
		}

		@Test
		void givenClearedRaceWithOtherCompletedLegs_whenRecomputeMatchScoresFromAllLegs_thenMatchScoreRecomputedFromRemainingLegs() {
			var homeTeam = createTeam("Home");
			var awayTeam = createTeam("Away");
			var match = createMatch(homeTeam, awayTeam);

			var clearedRace = createRace(match);
			clearedRace.setResults(List.of());

			var completedLeg = createRace(match);
			var homeDriver = createDriver("home_d");
			var awayDriver = createDriver("away_d");
			var r1 = createResult(completedLeg, homeDriver, 18);
			var r2 = createResult(completedLeg, awayDriver, 4);
			completedLeg.setResults(List.of(r1, r2));

			when(raceLineupRepository.findByRaceIdAndDriverId(completedLeg.getId(), homeDriver.getId()))
					.thenReturn(Optional.of(new RaceLineup(completedLeg, homeDriver, homeTeam)));
			when(raceLineupRepository.findByRaceIdAndDriverId(completedLeg.getId(), awayDriver.getId()))
					.thenReturn(Optional.of(new RaceLineup(completedLeg, awayDriver, awayTeam)));
			when(raceRepository.findByMatchId(match.getId())).thenReturn(List.of(clearedRace, completedLeg));

			match.setHomeScore(99);
			match.setAwayScore(99);

			scoringService.recomputeMatchScoresFromAllLegs(clearedRace);

			assertEquals(18, match.getHomeScore());
			assertEquals(4, match.getAwayScore());
		}

		@Test
		void givenClearedPlayoffRace_whenRecomputeMatchScoresFromAllLegs_thenPlayoffMatchupScoresRecomputedFromRemainingLegs() {
			var team1 = createTeam("Team1");
			var team2 = createTeam("Team2");
			var matchup = new PlayoffMatchup();
			matchup.setId(UUID.randomUUID());
			matchup.setTeam1(team1);
			matchup.setTeam2(team2);

			var clearedRace = new Race();
			clearedRace.setId(UUID.randomUUID());
			clearedRace.setPlayoffMatchup(matchup);
			clearedRace.setResults(List.of());

			var completedLeg = new Race();
			completedLeg.setId(UUID.randomUUID());
			completedLeg.setPlayoffMatchup(matchup);
			var d1 = createDriver("team1_d");
			var d2 = createDriver("team2_d");
			var r1 = createResult(completedLeg, d1, 25);
			var r2 = createResult(completedLeg, d2, 7);
			completedLeg.setResults(List.of(r1, r2));

			when(raceLineupRepository.findByRaceIdAndDriverId(completedLeg.getId(), d1.getId()))
					.thenReturn(Optional.of(new RaceLineup(completedLeg, d1, team1)));
			when(raceLineupRepository.findByRaceIdAndDriverId(completedLeg.getId(), d2.getId()))
					.thenReturn(Optional.of(new RaceLineup(completedLeg, d2, team2)));
			when(raceRepository.findByPlayoffMatchupId(matchup.getId()))
					.thenReturn(List.of(clearedRace, completedLeg));

			matchup.setHomeScore(99);
			matchup.setAwayScore(99);

			scoringService.recomputeMatchScoresFromAllLegs(clearedRace);

			assertEquals(25, matchup.getHomeScore());
			assertEquals(7, matchup.getAwayScore());
		}

		@Test
		void givenByeRace_whenAggregateMatchScores_thenReturnsWithoutScoring() {
			// given
			var homeTeam = createTeam("Home");
			var match = createMatch(homeTeam, null);
			match.setBye(true);
			var race = createRace(match);
			var driver = createDriver("d1");
			var r1 = createResult(race, driver, 10);
			race.setResults(List.of(r1));

			// when
			scoringService.aggregateMatchScores(race);

			// then — no scoring happens, no NPE, scores remain unset (null)
			assertNull(match.getHomeScore());
			assertNull(match.getAwayScore());
		}

		private Driver createDriver(String psnId) {
			var driver = new Driver(psnId, psnId);
			driver.setId(UUID.randomUUID());
			driver.setSeasonDrivers(new ArrayList<>());
			return driver;
		}

		private RaceResult createResult(Race race, Driver driver, int totalPoints) {
			var result = new RaceResult();
			result.setRace(race);
			result.setDriver(driver);
			result.setPointsTotal(totalPoints);
			return result;
		}
	}

	@Nested
	class IsDriverInTeamFallbackTest {

		@Test
		void givenNoRaceLineupAndDriverInTeamForDifferentSeason_whenIsDriverInTeam_thenReturnsFalse() {
			// given
			var season1 = new Season("Season 1");
			season1.setId(UUID.randomUUID());
			var season2 = new Season("Season 2");
			season2.setId(UUID.randomUUID());
			var team = new Team("TeamA", "TA");
			team.setId(UUID.randomUUID());

			var matchday = new Matchday();
			matchday.setId(UUID.randomUUID());
			// matchday's season is derived from phase.season; wire season2.
			matchday.setPhase(PhaseTestFixtures.regularPhase(season2, null, null));
			var match = new Match();
			match.setMatchday(matchday);
			var race = new Race();
			race.setId(UUID.randomUUID());
			race.setMatch(match);
			race.setMatchday(matchday);

			var driver = new Driver("d1", "d1");
			driver.setId(UUID.randomUUID());
			var sd = new SeasonDriver();
			sd.setTeam(team);
			sd.setSeason(season1); // driver assigned to teamA in season1, NOT season2
			driver.setSeasonDrivers(new ArrayList<>(List.of(sd)));

			var result = new RaceResult();
			result.setDriver(driver);
			result.setPointsTotal(10);

			when(raceLineupRepository.findByRaceIdAndDriverId(race.getId(), driver.getId()))
					.thenReturn(Optional.empty());
			when(raceRepository.findById(race.getId())).thenReturn(Optional.of(race));

			// when
			boolean inTeam = scoringService.isDriverInTeam(result, race.getId(), team.getId());

			// then
			assertFalse(inTeam);
		}

		@Test
		void givenNoRaceLineupAndDriverInTeamForCurrentSeason_whenIsDriverInTeam_thenReturnsTrue() {
			// given
			var season2 = new Season("Season 2");
			season2.setId(UUID.randomUUID());
			var team = new Team("TeamA", "TA");
			team.setId(UUID.randomUUID());

			var matchday = new Matchday();
			matchday.setId(UUID.randomUUID());
			// matchday's season is derived from phase.season; wire season2 (current).
			matchday.setPhase(PhaseTestFixtures.regularPhase(season2, null, null));
			var match = new Match();
			match.setMatchday(matchday);
			var race = new Race();
			race.setId(UUID.randomUUID());
			race.setMatch(match);
			race.setMatchday(matchday);

			var driver = new Driver("d1", "d1");
			driver.setId(UUID.randomUUID());
			var sd = new SeasonDriver();
			sd.setTeam(team);
			sd.setSeason(season2); // driver assigned to teamA in season2 (current)
			driver.setSeasonDrivers(new ArrayList<>(List.of(sd)));

			var result = new RaceResult();
			result.setDriver(driver);
			result.setPointsTotal(10);

			when(raceLineupRepository.findByRaceIdAndDriverId(race.getId(), driver.getId()))
					.thenReturn(Optional.empty());
			when(raceRepository.findById(race.getId())).thenReturn(Optional.of(race));

			// when
			boolean inTeam = scoringService.isDriverInTeam(result, race.getId(), team.getId());

			// then
			assertTrue(inTeam);
		}

		@Test
		void givenNoRaceLineupAndRaceNotFound_whenIsDriverInTeam_thenReturnsFalse() {
			// given
			var driver = new Driver("d1", "d1");
			driver.setId(UUID.randomUUID());
			driver.setSeasonDrivers(new ArrayList<>());
			var result = new RaceResult();
			result.setDriver(driver);
			var raceId = UUID.randomUUID();

			when(raceLineupRepository.findByRaceIdAndDriverId(raceId, driver.getId()))
					.thenReturn(Optional.empty());
			when(raceRepository.findById(raceId)).thenReturn(Optional.empty());

			// when
			boolean inTeam = scoringService.isDriverInTeam(result, raceId, UUID.randomUUID());

			// then
			assertFalse(inTeam);
		}
	}
}
