package org.ctc.domain.service;

import java.util.List;
import java.util.UUID;
import org.ctc.domain.model.*;
import org.ctc.domain.repository.RaceLineupRepository;
import org.ctc.domain.repository.RaceResultRepository;
import org.ctc.domain.repository.SeasonDriverRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DriverRankingServiceTest {

	@Mock
	private RaceResultRepository raceResultRepository;

	@Mock
	private SeasonDriverRepository seasonDriverRepository;

	@Mock
	private SeasonPhaseService seasonPhaseService;

	@Mock
	private RaceLineupRepository raceLineupRepository;

	@InjectMocks
	private DriverRankingService driverRankingService;

	private Season season;
	private Team tnr;
	private Driver panicpotato;
	private Driver levitius;
	// Phase-aware mock infrastructure (shared by refactored calculateRanking tests)
	private SeasonPhase regularPhase;

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

		// Shared: single REGULAR phase for legacy calculateRanking tests
		var rs = new RaceScoring("Phase58-Test-RS-Base", "20,15,10", "3,2,1", 2);
		rs.setId(UUID.randomUUID());
		var ms = new MatchScoring("Phase58-Test-MS-Base", 3, 1, 0);
		ms.setId(UUID.randomUUID());
		regularPhase = PhaseTestFixtures.regularPhase(season, rs, ms);
	}

	/**
	 * Sets up the phase-aware mocks for tests that call
	 * {@code aggregateAcrossPhases(List.of(regularPhase.getId()), season.getId())}.
	 */
	private void setupSingleRegularPhase(List<RaceResult> results) {
		when(seasonPhaseService.findById(regularPhase.getId())).thenReturn(regularPhase);
		when(raceResultRepository.findByRaceMatchdayPhaseId(regularPhase.getId())).thenReturn(results);
		when(raceResultRepository.findByRacePlayoffMatchupRoundPlayoffPhaseId(regularPhase.getId()))
				.thenReturn(List.of());
	}

	@Test
	void givenTwoDriversWithDifferentPoints_whenCalculateRanking_thenSortedByTotalPoints() {
		// given
		var race = new Race();
		race.setId(UUID.randomUUID());

		var result1 = createResult(race, panicpotato, 23, 1);
		var result2 = createResult(race, levitius, 11, 5);

		setupSingleRegularPhase(List.of(result1, result2));
		when(raceLineupRepository.findByDriverIdAndRaceMatchdaySeasonId(any(UUID.class), any(UUID.class)))
				.thenReturn(List.of());

		// when
		var rankings = driverRankingService.aggregateAcrossPhases(List.of(regularPhase.getId()), season.getId());

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
		var race1 = new Race();
		race1.setId(UUID.randomUUID());
		var race2 = new Race();
		race2.setId(UUID.randomUUID());

		var result1 = createResult(race1, panicpotato, 23, 1);
		var result2 = createResult(race2, panicpotato, 17, 2);

		setupSingleRegularPhase(List.of(result1, result2));
		when(raceLineupRepository.findByDriverIdAndRaceMatchdaySeasonId(panicpotato.getId(), season.getId()))
				.thenReturn(List.of());

		// when
		var rankings = driverRankingService.aggregateAcrossPhases(List.of(regularPhase.getId()), season.getId());

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
		setupSingleRegularPhase(List.of());

		// when
		var rankings = driverRankingService.aggregateAcrossPhases(List.of(regularPhase.getId()), season.getId());

		// then
		assertTrue(rankings.isEmpty());
	}

	@Test
	void givenDriverWithRaceLineup_whenCalculateRanking_thenTeamIncludedInRanking() {
		// given — team now resolved via RaceLineup (Source of Truth per CLAUDE.md), not SeasonDriver
		var race = new Race();
		race.setId(UUID.randomUUID());
		var result = createResult(race, panicpotato, 23, 1);

		var lineup = new RaceLineup(race, panicpotato, tnr);
		lineup.setId(UUID.randomUUID());

		setupSingleRegularPhase(List.of(result));
		when(raceLineupRepository.findByDriverIdAndRaceMatchdaySeasonId(panicpotato.getId(), season.getId()))
				.thenReturn(List.of(lineup));

		// when
		var rankings = driverRankingService.aggregateAcrossPhases(List.of(regularPhase.getId()), season.getId());

		// then
		assertEquals(tnr, rankings.get(0).getTeam());
	}

	@Test
	void givenTiedPointsWithDifferentRaceCounts_whenCalculateRanking_thenFewerRacesRankedFirst() {
		// given
		var race1 = new Race();
		race1.setId(UUID.randomUUID());
		var race2 = new Race();
		race2.setId(UUID.randomUUID());

		// panicpotato: 20 points in 1 race
		var result1 = createResult(race1, panicpotato, 20, 1);
		// levitius: 20 points in 2 races (10 each)
		var result2 = createResult(race1, levitius, 10, 3);
		var result3 = createResult(race2, levitius, 10, 4);

		setupSingleRegularPhase(List.of(result1, result2, result3));
		when(raceLineupRepository.findByDriverIdAndRaceMatchdaySeasonId(any(UUID.class), any(UUID.class)))
				.thenReturn(List.of());

		// when
		var rankings = driverRankingService.aggregateAcrossPhases(List.of(regularPhase.getId()), season.getId());

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


	@Test
	void givenRegularPhase_whenCalculateRankingForPhase_thenAggregatesViaMatchdayPhaseId() {
		// given
		var rs = new RaceScoring("Phase58-Test-RS", "20,15,10", "3,2,1", 2);
		rs.setId(UUID.randomUUID());
		var ms = new MatchScoring("Phase58-Test-MS", 3, 1, 0);
		ms.setId(UUID.randomUUID());
		var regular = PhaseTestFixtures.regularPhase(season, rs, ms);

		var race = new Race();
		race.setId(UUID.randomUUID());
		var result = createResult(race, panicpotato, 23, 1);

		when(seasonPhaseService.findById(regular.getId())).thenReturn(regular);
		when(raceResultRepository.findByRaceMatchdayPhaseId(regular.getId())).thenReturn(List.of(result));
		when(raceResultRepository.findByRacePlayoffMatchupRoundPlayoffPhaseId(regular.getId())).thenReturn(List.of());

		// when
		var rankings = driverRankingService.calculateRankingForPhase(regular.getId());

		// then
		assertThat(rankings).hasSize(1);
		assertThat(rankings.get(0).getDriver().getId()).isEqualTo(panicpotato.getId());
		assertThat(rankings.get(0).getTotalPoints()).isEqualTo(23);
		verify(raceResultRepository).findByRaceMatchdayPhaseId(regular.getId());
	}

	@Test
	void givenPlayoffPhase_whenCalculateRankingForPhase_thenAggregatesViaPlayoffMatchupChain() {
		// given
		var rs = new RaceScoring("Phase58-Test-RS2", "20,15,10", "3,2,1", 2);
		rs.setId(UUID.randomUUID());
		var ms = new MatchScoring("Phase58-Test-MS2", 3, 1, 0);
		ms.setId(UUID.randomUUID());
		var playoff = PhaseTestFixtures.playoffPhase(season, "Phase58-Test-Playoff", rs, ms);

		var race = new Race();
		race.setId(UUID.randomUUID());
		var result = createResult(race, panicpotato, 20, 1);

		when(seasonPhaseService.findById(playoff.getId())).thenReturn(playoff);
		when(raceResultRepository.findByRaceMatchdayPhaseId(playoff.getId())).thenReturn(List.of());
		when(raceResultRepository.findByRacePlayoffMatchupRoundPlayoffPhaseId(playoff.getId()))
				.thenReturn(List.of(result));

		// when
		var rankings = driverRankingService.calculateRankingForPhase(playoff.getId());

		// then
		assertThat(rankings).hasSize(1);
		assertThat(rankings.get(0).getTotalPoints()).isEqualTo(20);
		verify(raceResultRepository).findByRacePlayoffMatchupRoundPlayoffPhaseId(playoff.getId());
	}

	@Test
	void givenPerPhaseRanking_whenRaceLineupExists_thenTeamPopulatedFromLineup() {
		// given
		var rs = new RaceScoring("W1-RS", "20,15,10", "3,2,1", 2);
		rs.setId(UUID.randomUUID());
		var ms = new MatchScoring("W1-MS", 3, 1, 0);
		ms.setId(UUID.randomUUID());
		var regular = PhaseTestFixtures.regularPhase(season, rs, ms);

		var race = new Race();
		race.setId(UUID.randomUUID());
		var result = createResult(race, panicpotato, 23, 1);
		var lineup = new RaceLineup(race, panicpotato, tnr);

		when(seasonPhaseService.findById(regular.getId())).thenReturn(regular);
		when(raceResultRepository.findByRaceMatchdayPhaseId(regular.getId())).thenReturn(List.of(result));
		when(raceResultRepository.findByRacePlayoffMatchupRoundPlayoffPhaseId(regular.getId())).thenReturn(List.of());
		when(raceLineupRepository.findByRaceIdAndDriverId(race.getId(), panicpotato.getId()))
				.thenReturn(java.util.Optional.of(lineup));

		// when
		var rankings = driverRankingService.calculateRankingForPhase(regular.getId());

		// then
		assertThat(rankings).hasSize(1);
		assertThat(rankings.get(0).getTeam())
				.as("per-phase ranking must populate Team via RaceLineup (W-1 fix)")
				.isEqualTo(tnr);
	}

	@Test
	void givenMultiPhaseSeason_whenAggregateAcrossPhases_thenRegularTeamGuardsAttribution() {
		// given — driver runs in REGULAR and PLAYOFF; season-wide ranking attributes to REGULAR team
		var rs = new RaceScoring("Phase58-Test-RS3", "20,15,10", "3,2,1", 2);
		rs.setId(UUID.randomUUID());
		var ms = new MatchScoring("Phase58-Test-MS3", 3, 1, 0);
		ms.setId(UUID.randomUUID());
		var regular = PhaseTestFixtures.regularPhase(season, rs, ms);
		var playoff = PhaseTestFixtures.playoffPhase(season, "Phase58-Test-PO", rs, ms);

		var race1 = new Race(); race1.setId(UUID.randomUUID());
		var race2 = new Race(); race2.setId(UUID.randomUUID());
		var r1 = createResult(race1, panicpotato, 20, 1);
		var r2 = createResult(race2, panicpotato, 15, 2);

		// regular-phase lineup returns TNR as team
		var lineup = new RaceLineup(race1, panicpotato, tnr);
		lineup.setId(UUID.randomUUID());

		when(seasonPhaseService.findById(regular.getId())).thenReturn(regular);
		when(seasonPhaseService.findById(playoff.getId())).thenReturn(playoff);
		when(raceResultRepository.findByRaceMatchdayPhaseId(regular.getId())).thenReturn(List.of(r1));
		when(raceResultRepository.findByRacePlayoffMatchupRoundPlayoffPhaseId(regular.getId())).thenReturn(List.of());
		when(raceResultRepository.findByRaceMatchdayPhaseId(playoff.getId())).thenReturn(List.of());
		when(raceResultRepository.findByRacePlayoffMatchupRoundPlayoffPhaseId(playoff.getId())).thenReturn(List.of(r2));
		when(raceLineupRepository.findByDriverIdAndRaceMatchdaySeasonId(panicpotato.getId(), season.getId()))
				.thenReturn(List.of(lineup));

		// when
		var rankings = driverRankingService.aggregateAcrossPhases(
				List.of(regular.getId(), playoff.getId()), season.getId());

		// then — driver appears once with merged totals and REGULAR team (D-08 via RaceLineup)
		assertThat(rankings).hasSize(1);
		assertThat(rankings.get(0).getDriver().getId()).isEqualTo(panicpotato.getId());
		assertThat(rankings.get(0).getTotalPoints()).isEqualTo(35);
		assertThat(rankings.get(0).getTeam()).isEqualTo(tnr);
	}

	@Test
	void givenStandInWithoutRegularPhaseTeam_whenAggregateAcrossPhases_thenRaceLineupFallback() {
		// given — stand-in driver only in PLAYOFF, no regular-phase lineup; falls back to RaceLineup
		var rs = new RaceScoring("Phase58-Test-RS4", "20,15,10", "3,2,1", 2);
		rs.setId(UUID.randomUUID());
		var ms = new MatchScoring("Phase58-Test-MS4", 3, 1, 0);
		ms.setId(UUID.randomUUID());
		var playoff = PhaseTestFixtures.playoffPhase(season, "Phase58-Test-PO2", rs, ms);

		var race = new Race(); race.setId(UUID.randomUUID());
		var result = createResult(race, levitius, 15, 2);

		// RaceLineup fallback returns TNR as stand-in team
		var lineup = new RaceLineup(race, levitius, tnr);
		lineup.setId(UUID.randomUUID());

		when(seasonPhaseService.findById(playoff.getId())).thenReturn(playoff);
		when(raceResultRepository.findByRaceMatchdayPhaseId(playoff.getId())).thenReturn(List.of());
		when(raceResultRepository.findByRacePlayoffMatchupRoundPlayoffPhaseId(playoff.getId()))
				.thenReturn(List.of(result));
		when(raceLineupRepository.findByDriverIdAndRaceMatchdaySeasonId(levitius.getId(), season.getId()))
				.thenReturn(List.of(lineup));

		// when
		var rankings = driverRankingService.aggregateAcrossPhases(List.of(playoff.getId()), season.getId());

		// then — team comes from RaceLineup fallback
		assertThat(rankings).hasSize(1);
		assertThat(rankings.get(0).getDriver().getId()).isEqualTo(levitius.getId());
		assertThat(rankings.get(0).getTeam()).isEqualTo(tnr);
	}

	@Test
	void givenPlacementPhase_whenCalculateRankingForPhase_thenIncludesPlacementResults() {
		// given — PLACEMENT phase results flow into per-phase ranking
		var rs = new RaceScoring("Phase58-Test-RS6", "20,15,10", "3,2,1", 2);
		rs.setId(UUID.randomUUID());
		var ms = new MatchScoring("Phase58-Test-MS6", 3, 1, 0);
		ms.setId(UUID.randomUUID());
		var placement = new SeasonPhase(season, PhaseType.PLACEMENT, PhaseLayout.LEAGUE, 20);
		placement.setId(UUID.randomUUID());

		var race = new Race(); race.setId(UUID.randomUUID());
		var result = createResult(race, panicpotato, 10, 3);

		when(seasonPhaseService.findById(placement.getId())).thenReturn(placement);
		when(raceResultRepository.findByRaceMatchdayPhaseId(placement.getId())).thenReturn(List.of(result));
		when(raceResultRepository.findByRacePlayoffMatchupRoundPlayoffPhaseId(placement.getId())).thenReturn(List.of());

		// when
		var rankings = driverRankingService.calculateRankingForPhase(placement.getId());

		// then — PLACEMENT results included (D-07: all phase types count)
		assertThat(rankings).hasSize(1);
		assertThat(rankings.get(0).getTotalPoints()).isEqualTo(10);
	}

	@Test
	void givenAggregateAcrossPhases_whenDriverHasResultsInBothRegularAndPlayoff_thenSinglyListedWithMergedTotalPoints() {
		// given — same driver in REGULAR and PLAYOFF; should appear once with sum of points (D-09 merge logic)
		var rs = new RaceScoring("Phase58-Test-RS7", "20,15,10", "3,2,1", 2);
		rs.setId(UUID.randomUUID());
		var ms = new MatchScoring("Phase58-Test-MS7", 3, 1, 0);
		ms.setId(UUID.randomUUID());
		var regular = PhaseTestFixtures.regularPhase(season, rs, ms);
		var playoff = PhaseTestFixtures.playoffPhase(season, "Phase58-Test-PO4", rs, ms);

		var race1 = new Race(); race1.setId(UUID.randomUUID());
		var race2 = new Race(); race2.setId(UUID.randomUUID());
		var r1 = createResult(race1, panicpotato, 20, 1);
		var r2 = createResult(race2, panicpotato, 15, 2);
		var r3 = createResult(race1, levitius, 10, 3);

		when(seasonPhaseService.findById(regular.getId())).thenReturn(regular);
		when(seasonPhaseService.findById(playoff.getId())).thenReturn(playoff);
		when(raceResultRepository.findByRaceMatchdayPhaseId(regular.getId())).thenReturn(List.of(r1, r3));
		when(raceResultRepository.findByRacePlayoffMatchupRoundPlayoffPhaseId(regular.getId())).thenReturn(List.of());
		when(raceResultRepository.findByRaceMatchdayPhaseId(playoff.getId())).thenReturn(List.of());
		when(raceResultRepository.findByRacePlayoffMatchupRoundPlayoffPhaseId(playoff.getId())).thenReturn(List.of(r2));
		when(raceLineupRepository.findByDriverIdAndRaceMatchdaySeasonId(any(UUID.class), any(UUID.class)))
				.thenReturn(List.of());

		// when
		var rankings = driverRankingService.aggregateAcrossPhases(
				List.of(regular.getId(), playoff.getId()), season.getId());

		// then — panicpotato: 20+15=35 total; levitius: 10; both singly listed (not duplicated)
		assertThat(rankings).hasSize(2);
		var panicRanking = rankings.stream()
				.filter(r -> r.getDriver().getId().equals(panicpotato.getId()))
				.findFirst().orElseThrow();
		assertThat(panicRanking.getTotalPoints()).isEqualTo(35);
		assertThat(panicRanking.getRacesCount()).isEqualTo(2);
	}


	/**
	 * D-19 TRACKED BEHAVIOR CHANGE: calculateAlltimeRanking(seasonIds) must include
	 * PLAYOFF-matchup-linked race results (not only REGULAR-phase results).
	 *
	 * <p>RED gate: today's implementation calls
	 * {@code findByRacePlayoffMatchupIsNullAndRaceMatchdaySeasonIdIn(seasonIds)} which excludes
	 * any RaceResult whose Race has a non-null playoffMatchup. The test expects the alltime
	 * races count to be greater than the REGULAR-only races count. This assertion FAILS
	 * until D-19 (findByRaceMatchdaySeasonIdIn without IsNull) is implemented.
	 */
	@Test
	void givenSeasonWithPlayoffPhase_whenCalculateAlltimeRanking_thenIncludesPlayoffResults() {
		// given — 1 REGULAR race + 1 PLAYOFF race for panicpotato
		var regularRace = new Race();
		regularRace.setId(UUID.randomUUID());
		var playoffRace = new Race();
		playoffRace.setId(UUID.randomUUID());

		var regularResult = createResult(regularRace, panicpotato, 20, 1);
		var playoffResult = createResult(playoffRace, panicpotato, 15, 2);

		var sd = new SeasonDriver(season, panicpotato, tnr);

		// findByRaceMatchdaySeasonIdIn (no IsNull filter) returns BOTH results.
		// The old IsNull-filtered finder would only return regularResult — causing racesCount == 1.
		// The new finder returns both → racesCount == 2, proving PLAYOFF results are included.
		when(raceResultRepository.findByRaceMatchdaySeasonIdIn(List.of(season.getId())))
				.thenReturn(List.of(regularResult, playoffResult));
		when(seasonDriverRepository.findBySeasonIdIn(List.of(season.getId())))
				.thenReturn(List.of(sd));

		// when
		var alltime = driverRankingService.calculateAlltimeRanking(List.of(season.getId()));

		// then — D-19: both REGULAR (1) and PLAYOFF (1) races counted → racesCount == 2
		assertThat(alltime).isNotEmpty();
		var panicAlltime = alltime.stream()
				.filter(r -> r.getDriver().getId().equals(panicpotato.getId()))
				.findFirst()
				.orElseThrow(() -> new AssertionError("panicpotato not found in alltime ranking"));

		assertThat(panicAlltime.getRacesCount())
				.as("D-19: alltime racesCount must include REGULAR (1) + PLAYOFF (1) = 2")
				.isGreaterThan(1);
	}

	@Test
	void givenDoppelrollenGuest_whenCalculateRankingForPhase_thenAttributedToHomeTeam() {
		// given — driver rostered in homeTeam (SeasonDriver) but guests for guestTeam in this race
		var rs = new RaceScoring("Guest-RS1", "20,15,10", "3,2,1", 2);
		rs.setId(UUID.randomUUID());
		var ms = new MatchScoring("Guest-MS1", 3, 1, 0);
		ms.setId(UUID.randomUUID());
		var regular = PhaseTestFixtures.regularPhase(season, rs, ms);

		var guestTeam = new Team("Guest Racing", "GST");
		guestTeam.setId(UUID.randomUUID());

		var race = new Race();
		race.setId(UUID.randomUUID());
		var result = createResult(race, panicpotato, 20, 1);
		var sd = new SeasonDriver(season, panicpotato, tnr);

		when(seasonPhaseService.findById(regular.getId())).thenReturn(regular);
		when(raceResultRepository.findByRaceMatchdayPhaseId(regular.getId())).thenReturn(List.of(result));
		when(raceResultRepository.findByRacePlayoffMatchupRoundPlayoffPhaseId(regular.getId())).thenReturn(List.of());
		when(seasonDriverRepository.findBySeasonIdAndDriverId(season.getId(), panicpotato.getId()))
				.thenReturn(java.util.Optional.of(sd));

		// when
		var rankings = driverRankingService.calculateRankingForPhase(regular.getId());

		// then — home-first (D-01): attributed to roster team, not guest team
		assertThat(rankings).hasSize(1);
		assertThat(rankings.get(0).getTeam()).isEqualTo(tnr);
	}

	@Test
	void givenPureGuest_whenCalculateRankingForPhase_thenAttributedToFieldingTeam() {
		// given — driver has NO SeasonDriver, only a guest RaceLineup for a sub-team
		var rs = new RaceScoring("Guest-RS2", "20,15,10", "3,2,1", 2);
		rs.setId(UUID.randomUUID());
		var ms = new MatchScoring("Guest-MS2", 3, 1, 0);
		ms.setId(UUID.randomUUID());
		var regular = PhaseTestFixtures.regularPhase(season, rs, ms);

		var parentTeam = new Team("Bravo Racing", "BRV");
		parentTeam.setId(UUID.randomUUID());
		var subTeam = new Team("Bravo Racing 1", "BRV 1", parentTeam);
		subTeam.setId(UUID.randomUUID());

		var race = new Race();
		race.setId(UUID.randomUUID());
		var result = createResult(race, levitius, 18, 2);
		var lineup = new RaceLineup(race, levitius, subTeam, true);

		when(seasonPhaseService.findById(regular.getId())).thenReturn(regular);
		when(raceResultRepository.findByRaceMatchdayPhaseId(regular.getId())).thenReturn(List.of(result));
		when(raceResultRepository.findByRacePlayoffMatchupRoundPlayoffPhaseId(regular.getId())).thenReturn(List.of());
		when(seasonDriverRepository.findBySeasonIdAndDriverId(season.getId(), levitius.getId()))
				.thenReturn(java.util.Optional.empty());
		when(raceLineupRepository.findByRaceIdAndDriverId(race.getId(), levitius.getId()))
				.thenReturn(java.util.Optional.of(lineup));

		// when
		var rankings = driverRankingService.calculateRankingForPhase(regular.getId());

		// then — fallback fielding (D-02): sub-team rolled up to parent, never null
		assertThat(rankings).hasSize(1);
		assertThat(rankings.get(0).getTeam()).isEqualTo(parentTeam);
	}

	@Test
	void givenDoppelrollenInAggregate_whenAggregateAcrossPhases_thenSingleRowUnderHomeTeam() {
		// given — doppelrollen guest across the season aggregate
		var rs = new RaceScoring("Guest-RS3", "20,15,10", "3,2,1", 2);
		rs.setId(UUID.randomUUID());
		var ms = new MatchScoring("Guest-MS3", 3, 1, 0);
		ms.setId(UUID.randomUUID());
		var regular = PhaseTestFixtures.regularPhase(season, rs, ms);

		var race = new Race();
		race.setId(UUID.randomUUID());
		var result = createResult(race, panicpotato, 20, 1);
		var sd = new SeasonDriver(season, panicpotato, tnr);

		when(seasonPhaseService.findById(regular.getId())).thenReturn(regular);
		when(raceResultRepository.findByRaceMatchdayPhaseId(regular.getId())).thenReturn(List.of(result));
		when(raceResultRepository.findByRacePlayoffMatchupRoundPlayoffPhaseId(regular.getId())).thenReturn(List.of());
		when(seasonDriverRepository.findBySeasonIdAndDriverId(season.getId(), panicpotato.getId()))
				.thenReturn(java.util.Optional.of(sd));

		// when
		var rankings = driverRankingService.aggregateAcrossPhases(List.of(regular.getId()), season.getId());

		// then — D-03: single row, home team
		assertThat(rankings).hasSize(1);
		assertThat(rankings.get(0).getTeam()).isEqualTo(tnr);
	}

	@Test
	void givenPureGuestInAlltime_whenCalculateAlltimeRanking_thenTeamNotNull() {
		// given — pure guest absent from SeasonDriver, present in RaceLineup (sub-team)
		var parentTeam = new Team("Bravo Racing", "BRV");
		parentTeam.setId(UUID.randomUUID());
		var subTeam = new Team("Bravo Racing 1", "BRV 1", parentTeam);
		subTeam.setId(UUID.randomUUID());

		var race = new Race();
		race.setId(UUID.randomUUID());
		var result = createResult(race, levitius, 12, 3);
		var lineup = new RaceLineup(race, levitius, subTeam, true);

		when(raceResultRepository.findByRacePlayoffMatchupIsNull()).thenReturn(List.of(result));
		when(seasonDriverRepository.findAll()).thenReturn(List.of());
		when(raceLineupRepository.findByDriverId(levitius.getId())).thenReturn(List.of(lineup));

		// when
		var rankings = driverRankingService.calculateAlltimeRanking();

		// then — D-04: alltime no longer null for a pure guest
		assertThat(rankings).hasSize(1);
		assertThat(rankings.get(0).getTeam()).isNotNull();
		assertThat(rankings.get(0).getTeam()).isEqualTo(parentTeam);
	}

	@Test
	void givenNormalRosterDriver_whenCalculateRankingForPhase_thenUnchangedTeam() {
		// given — ordinary roster driver: SeasonDriver team == lineup team
		var rs = new RaceScoring("Guest-RS4", "20,15,10", "3,2,1", 2);
		rs.setId(UUID.randomUUID());
		var ms = new MatchScoring("Guest-MS4", 3, 1, 0);
		ms.setId(UUID.randomUUID());
		var regular = PhaseTestFixtures.regularPhase(season, rs, ms);

		var race = new Race();
		race.setId(UUID.randomUUID());
		var result = createResult(race, panicpotato, 25, 1);
		var sd = new SeasonDriver(season, panicpotato, tnr);

		when(seasonPhaseService.findById(regular.getId())).thenReturn(regular);
		when(raceResultRepository.findByRaceMatchdayPhaseId(regular.getId())).thenReturn(List.of(result));
		when(raceResultRepository.findByRacePlayoffMatchupRoundPlayoffPhaseId(regular.getId())).thenReturn(List.of());
		when(seasonDriverRepository.findBySeasonIdAndDriverId(season.getId(), panicpotato.getId()))
				.thenReturn(java.util.Optional.of(sd));

		// when
		var rankings = driverRankingService.calculateRankingForPhase(regular.getId());

		// then — regression: roster attribution unchanged
		assertThat(rankings).hasSize(1);
		assertThat(rankings.get(0).getTeam()).isEqualTo(tnr);
	}

	@Test
	void givenDriverWithHomeAndGuestRace_whenAggregateAcrossPhases_thenSingleRowPointsSummed() {
		// given — driver races for own team AND guests in the same season
		var rs = new RaceScoring("Guest-RS5", "20,15,10", "3,2,1", 2);
		rs.setId(UUID.randomUUID());
		var ms = new MatchScoring("Guest-MS5", 3, 1, 0);
		ms.setId(UUID.randomUUID());
		var regular = PhaseTestFixtures.regularPhase(season, rs, ms);

		var raceHome = new Race();
		raceHome.setId(UUID.randomUUID());
		var raceGuest = new Race();
		raceGuest.setId(UUID.randomUUID());
		var rHome = createResult(raceHome, panicpotato, 20, 1);
		var rGuest = createResult(raceGuest, panicpotato, 12, 3);
		var sd = new SeasonDriver(season, panicpotato, tnr);

		when(seasonPhaseService.findById(regular.getId())).thenReturn(regular);
		when(raceResultRepository.findByRaceMatchdayPhaseId(regular.getId())).thenReturn(List.of(rHome, rGuest));
		when(raceResultRepository.findByRacePlayoffMatchupRoundPlayoffPhaseId(regular.getId())).thenReturn(List.of());
		when(seasonDriverRepository.findBySeasonIdAndDriverId(season.getId(), panicpotato.getId()))
				.thenReturn(java.util.Optional.of(sd));

		// when
		var rankings = driverRankingService.aggregateAcrossPhases(List.of(regular.getId()), season.getId());

		// then — D-06/D-07: ONE additive row, both races counted fully
		assertThat(rankings).hasSize(1);
		assertThat(rankings.get(0).getTotalPoints()).isEqualTo(32);
		assertThat(rankings.get(0).getRacesCount()).isEqualTo(2);
		assertThat(rankings.get(0).getTeam()).isEqualTo(tnr);
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
