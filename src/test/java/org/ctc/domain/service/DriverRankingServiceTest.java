package org.ctc.domain.service;

import org.ctc.domain.model.*;
import org.ctc.domain.repository.RaceResultRepository;
import org.ctc.domain.repository.SeasonDriverRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DriverRankingServiceTest {

	@Mock
	private RaceResultRepository raceResultRepository;

	@Mock
	private SeasonDriverRepository seasonDriverRepository;

	@InjectMocks
	private DriverRankingService driverRankingService;

	private Season season;
	private Team tnr;
	private Driver panicpotato;
	private Driver levitius;

	@BeforeEach
	void setUp() {
		season = new Season("2026");
		season.setId(UUID.randomUUID());

		tnr = new Team("The Neutrals Racing", "TNR");
		tnr.setId(UUID.randomUUID());

		panicpotato = new Driver("panicpotato17", "panicpotato17");
		panicpotato.setId(UUID.randomUUID());

		levitius = new Driver("LEVITIUS", "LEVITIUS");
		levitius.setId(UUID.randomUUID());
	}

	@Test
	void givenTwoDriversWithDifferentPoints_whenCalculateRanking_thenSortedByTotalPoints() {
		// given
		var sd1 = new SeasonDriver(season, panicpotato, tnr);
		var sd2 = new SeasonDriver(season, levitius, tnr);

		var race = new Race();
		race.setId(UUID.randomUUID());

		var result1 = createResult(race, panicpotato, 23, 1);
		var result2 = createResult(race, levitius, 11, 5);

		when(raceResultRepository.findByRaceMatchdaySeasonId(season.getId()))
				.thenReturn(List.of(result1, result2));
		when(seasonDriverRepository.findBySeasonId(season.getId()))
				.thenReturn(List.of(sd1, sd2));

		// when
		var rankings = driverRankingService.calculateRanking(season.getId());

		// then
		assertEquals(2, rankings.size());
		assertEquals(panicpotato.getId(), rankings.get(0).getDriver().getId());
		assertEquals(23, rankings.get(0).getTotalPoints());
		assertEquals(1, rankings.get(0).getRacesCount());
		assertEquals(1, rankings.get(0).getBestPosition());

		assertEquals(levitius.getId(), rankings.get(1).getDriver().getId());
		assertEquals(11, rankings.get(1).getTotalPoints());
		assertEquals(5, rankings.get(1).getBestPosition());
	}

	@Test
	void givenDriverWithMultipleRaces_whenCalculateRanking_thenPointsAccumulated() {
		// given
		var sd = new SeasonDriver(season, panicpotato, tnr);

		var race1 = new Race();
		race1.setId(UUID.randomUUID());
		var race2 = new Race();
		race2.setId(UUID.randomUUID());

		var result1 = createResult(race1, panicpotato, 23, 1);
		var result2 = createResult(race2, panicpotato, 17, 2);

		when(raceResultRepository.findByRaceMatchdaySeasonId(season.getId()))
				.thenReturn(List.of(result1, result2));
		when(seasonDriverRepository.findBySeasonId(season.getId()))
				.thenReturn(List.of(sd));

		// when
		var rankings = driverRankingService.calculateRanking(season.getId());

		// then
		assertEquals(1, rankings.size());
		assertEquals(40, rankings.get(0).getTotalPoints());
		assertEquals(2, rankings.get(0).getRacesCount());
		assertEquals(20.0, rankings.get(0).getAveragePoints());
		assertEquals(1, rankings.get(0).getBestPosition());
	}

	@Test
	void givenNoResults_whenCalculateRanking_thenReturnsEmptyList() {
		// given
		when(raceResultRepository.findByRaceMatchdaySeasonId(season.getId()))
				.thenReturn(List.of());
		when(seasonDriverRepository.findBySeasonId(season.getId()))
				.thenReturn(List.of());

		// when
		var rankings = driverRankingService.calculateRanking(season.getId());

		// then
		assertTrue(rankings.isEmpty());
	}

	@Test
	void givenDriverWithSeasonDriver_whenCalculateRanking_thenTeamIncludedInRanking() {
		// given
		var sd = new SeasonDriver(season, panicpotato, tnr);

		var race = new Race();
		race.setId(UUID.randomUUID());
		var result = createResult(race, panicpotato, 23, 1);

		when(raceResultRepository.findByRaceMatchdaySeasonId(season.getId()))
				.thenReturn(List.of(result));
		when(seasonDriverRepository.findBySeasonId(season.getId()))
				.thenReturn(List.of(sd));

		// when
		var rankings = driverRankingService.calculateRanking(season.getId());

		// then
		assertEquals(tnr, rankings.get(0).getTeam());
	}

	@Test
	void givenTiedPointsWithDifferentRaceCounts_whenCalculateRanking_thenFewerRacesRankedFirst() {
		// given
		var sd1 = new SeasonDriver(season, panicpotato, tnr);
		var sd2 = new SeasonDriver(season, levitius, tnr);

		var race1 = new Race();
		race1.setId(UUID.randomUUID());
		var race2 = new Race();
		race2.setId(UUID.randomUUID());

		// panicpotato: 20 points in 1 race
		var result1 = createResult(race1, panicpotato, 20, 1);
		// levitius: 20 points in 2 races (10 each)
		var result2 = createResult(race1, levitius, 10, 3);
		var result3 = createResult(race2, levitius, 10, 4);

		when(raceResultRepository.findByRaceMatchdaySeasonId(season.getId()))
				.thenReturn(List.of(result1, result2, result3));
		when(seasonDriverRepository.findBySeasonId(season.getId()))
				.thenReturn(List.of(sd1, sd2));

		// when
		var rankings = driverRankingService.calculateRanking(season.getId());

		// then
		// Same total points, panicpotato has fewer races → ranked first
		assertEquals(panicpotato.getId(), rankings.get(0).getDriver().getId());
	}

	@Test
	void givenDriverAcrossMultipleSeasons_whenCalculateAlltimeRanking_thenPointsAggregated() {
		// given
		var season2 = new Season("2025");
		season2.setId(UUID.randomUUID());

		var sd1 = new SeasonDriver(season, panicpotato, tnr);
		var sd2 = new SeasonDriver(season2, panicpotato, tnr);

		var race1 = new Race();
		race1.setId(UUID.randomUUID());
		var race2 = new Race();
		race2.setId(UUID.randomUUID());

		var result1 = createResult(race1, panicpotato, 23, 1);
		var result2 = createResult(race2, panicpotato, 17, 2);

		when(raceResultRepository.findByRacePlayoffMatchupIsNull())
				.thenReturn(List.of(result1, result2));
		when(seasonDriverRepository.findAll())
				.thenReturn(List.of(sd1, sd2));

		// when
		var rankings = driverRankingService.calculateAlltimeRanking();

		// then
		assertEquals(1, rankings.size());
		assertEquals(40, rankings.get(0).getTotalPoints());
		assertEquals(2, rankings.get(0).getRacesCount());
		assertEquals(1, rankings.get(0).getBestPosition());
	}

	@Test
	void givenDriverInMultipleTeams_whenCalculateAlltimeRanking_thenShowsMostRecentTeam() {
		// given
		var season2 = new Season("2025");
		season2.setId(UUID.randomUUID());

		var p1r = new Team("Project One Racing", "P1R");
		p1r.setId(UUID.randomUUID());

		// panicpotato was in P1R in 2025, TNR in 2026
		var sd1 = new SeasonDriver(season2, panicpotato, p1r);
		var sd2 = new SeasonDriver(season, panicpotato, tnr);

		var race = new Race();
		race.setId(UUID.randomUUID());
		var result = createResult(race, panicpotato, 23, 1);

		when(raceResultRepository.findByRacePlayoffMatchupIsNull())
				.thenReturn(List.of(result));
		when(seasonDriverRepository.findAll())
				.thenReturn(List.of(sd1, sd2));

		// when
		var rankings = driverRankingService.calculateAlltimeRanking();

		// then
		// Season "2026" > "2025" alphabetically, so TNR is most recent
		assertEquals(tnr, rankings.get(0).getTeam());
	}

	@Test
	void givenDriverInSubTeam_whenCalculateAlltimeRanking_thenShowsParentTeam() {
		// given
		var clr = new Team("Community League Racing", "CLR");
		clr.setId(UUID.randomUUID());
		var clr1 = new Team("CLR 1", "CLR 1", clr);
		clr1.setId(UUID.randomUUID());

		var sd = new SeasonDriver(season, panicpotato, clr1);

		var race = new Race();
		race.setId(UUID.randomUUID());
		var result = createResult(race, panicpotato, 23, 1);

		when(raceResultRepository.findByRacePlayoffMatchupIsNull())
				.thenReturn(List.of(result));
		when(seasonDriverRepository.findAll())
				.thenReturn(List.of(sd));

		// when
		var rankings = driverRankingService.calculateAlltimeRanking();

		// then
		// Should show parent CLR, not sub-team CLR 1
		assertEquals(clr, rankings.get(0).getTeam());
	}

	private RaceResult createResult(Race race, Driver driver, int totalPoints, int position) {
		var result = new RaceResult();
		result.setRace(race);
		result.setDriver(driver);
		result.setPosition(position);
		result.setQualiPosition(position);
		result.setPointsTotal(totalPoints);
		result.setPointsRace(totalPoints);
		return result;
	}
}
