package org.ctc.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.UUID;
import org.ctc.domain.model.PhaseType;
import org.ctc.domain.model.Race;
import org.ctc.domain.model.Season;
import org.ctc.domain.repository.DriverRepository;
import org.ctc.domain.repository.RaceLineupRepository;
import org.ctc.domain.repository.RaceRepository;
import org.ctc.domain.repository.RaceResultRepository;
import org.ctc.domain.repository.SeasonRepository;
import org.ctc.testsupport.CtcDevSpringBootContext;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@CtcDevSpringBootContext
@Tag("integration")
@Transactional
class DriverRankingServiceGuestIT {

	@Autowired private DriverRankingService driverRankingService;
	@Autowired private ScoringService scoringService;
	@Autowired private SeasonPhaseService seasonPhaseService;
	@Autowired private SeasonRepository seasonRepository;
	@Autowired private RaceRepository raceRepository;
	@Autowired private RaceLineupRepository raceLineupRepository;
	@Autowired private RaceResultRepository raceResultRepository;
	@Autowired private DriverRepository driverRepository;
	@Autowired private EntityManager entityManager;

	@Test
	void givenGuestResultInRace_whenAggregateMatchScores_thenCountsForFieldingTeamScore() {
		// given — doppelrollen guest Test_Doppel_1 fielded for the away team of match1
		var doppel = driverRepository.findByPsnId("Test_Doppel_1").orElseThrow();
		var guestLineup = raceLineupRepository.findByDriverId(doppel.getId()).stream()
				.filter(rl -> rl.isGuest()).findFirst().orElseThrow();
		Race race = raceRepository.findById(guestLineup.getRace().getId()).orElseThrow();

		// when
		scoringService.aggregateMatchScores(race);

		// then — guest fields for the away team and contributes to its match score
		var match = race.getMatch();
		UUID awayTeamId = match.getAwayTeam().getId();
		var guestResult = raceResultRepository.findByRaceIdAndDriverId(race.getId(), doppel.getId()).orElseThrow();
		assertThat(scoringService.isDriverInTeam(guestResult, race.getId(), awayTeamId)).isTrue();
		assertThat(match.getAwayScore()).isGreaterThan(0);
		assertThat(match.getAwayScore()).isGreaterThanOrEqualTo(guestResult.getPointsTotal());
	}

	@Test
	void givenPureGuestWithNoSeasonDriver_whenAggregateAcrossPhases_thenAppearsInRankingWithFieldingTeam() {
		// given
		var season = testSeason();
		UUID regularPhaseId = regularPhaseId(season.getId());
		var guestDriver = driverRepository.findByPsnId("Test_Guest_1").orElseThrow();

		// when
		var rankings = driverRankingService.aggregateAcrossPhases(List.of(regularPhaseId), season.getId());

		// then — pure guest appears with the fielding parent team (sub-team rolled up), never null
		var guestRow = rankings.stream()
				.filter(r -> r.getDriver().getPsnId().equals("Test_Guest_1"))
				.findFirst().orElseThrow();
		assertThat(guestRow.getTeam()).isNotNull();
		assertThat(guestRow.getTeam().getShortName()).isEqualTo("T-BRV");
		int expectedGuestPoints = raceResultRepository.findByDriverId(guestDriver.getId()).stream()
				.mapToInt(r -> r.getPointsTotal()).sum();
		assertThat(guestRow.getTotalPoints()).isEqualTo(expectedGuestPoints);

		// and — doppelrollen guest appears once under its HOME team (home-first)
		var doppelRows = rankings.stream()
				.filter(r -> r.getDriver().getPsnId().equals("Test_Doppel_1"))
				.toList();
		assertThat(doppelRows).hasSize(1);
		assertThat(doppelRows.get(0).getTeam().getShortName()).isEqualTo("T-ALF");
	}

	@Test
	void givenGuestResultSavedTwice_whenAggregateMatchScores_thenScoresAreIdempotent() {
		// given — the doppelrollen guest's race
		var doppel = driverRepository.findByPsnId("Test_Doppel_1").orElseThrow();
		var doppelLineup = raceLineupRepository.findByDriverId(doppel.getId()).stream()
				.filter(rl -> rl.isGuest()).findFirst().orElseThrow();
		Race race = raceRepository.findById(doppelLineup.getRace().getId()).orElseThrow();

		// when — aggregate twice (recompute-from-all-legs replaces, never accumulates)
		scoringService.aggregateMatchScores(race);
		int homeAfterFirst = race.getMatch().getHomeScore();
		int awayAfterFirst = race.getMatch().getAwayScore();
		scoringService.aggregateMatchScores(race);
		int homeAfterSecond = race.getMatch().getHomeScore();
		int awayAfterSecond = race.getMatch().getAwayScore();

		// then — idempotent
		assertThat(homeAfterSecond).isEqualTo(homeAfterFirst);
		assertThat(awayAfterSecond).isEqualTo(awayAfterFirst);

		// and — removing the pure guest makes both its personal credit and team contribution disappear
		var season = testSeason();
		UUID regularPhaseId = regularPhaseId(season.getId());
		var guestDriver = driverRepository.findByPsnId("Test_Guest_1").orElseThrow();
		UUID guestRaceId = raceLineupRepository.findByDriverId(guestDriver.getId()).stream()
				.filter(rl -> rl.isGuest()).findFirst().orElseThrow().getRace().getId();

		Race baseRace = raceRepository.findById(guestRaceId).orElseThrow();
		scoringService.aggregateMatchScores(baseRace);
		int awayBeforeRemoval = baseRace.getMatch().getAwayScore();
		int guestPoints = raceResultRepository.findByRaceIdAndDriverId(guestRaceId, guestDriver.getId())
				.orElseThrow().getPointsTotal();

		// Detach the managed results collection first so orphanRemoval does not re-persist the row.
		entityManager.clear();
		raceResultRepository.findByRaceIdAndDriverId(guestRaceId, guestDriver.getId())
				.ifPresent(raceResultRepository::delete);
		raceLineupRepository.findByRaceIdAndDriverId(guestRaceId, guestDriver.getId())
				.ifPresent(raceLineupRepository::delete);
		raceResultRepository.flush();
		entityManager.clear();

		Race reloadedGuestRace = raceRepository.findById(guestRaceId).orElseThrow();
		scoringService.aggregateMatchScores(reloadedGuestRace);

		assertThat(raceResultRepository.findByDriverId(guestDriver.getId())).isEmpty();
		assertThat(reloadedGuestRace.getMatch().getAwayScore()).isEqualTo(awayBeforeRemoval - guestPoints);
		var rankingsAfter = driverRankingService.aggregateAcrossPhases(List.of(regularPhaseId), season.getId());
		assertThat(rankingsAfter).noneMatch(r -> r.getDriver().getPsnId().equals("Test_Guest_1"));
	}

	@Test
	void givenPureGuestInAlltimeScope_whenCalculateAlltimeRankingForSeason_thenTeamIsNotNull() {
		// given
		var season = testSeason();

		// when
		var alltime = driverRankingService.calculateAlltimeRanking(List.of(season.getId()));

		// then — alltime no longer leaves a pure guest with team == null (Plan-02 D-04 fallback)
		var guestRow = alltime.stream()
				.filter(r -> r.getDriver().getPsnId().equals("Test_Guest_1"))
				.findFirst().orElseThrow();
		assertThat(guestRow.getTeam()).isNotNull();
		assertThat(guestRow.getTeam().getShortName()).isEqualTo("T-BRV");
	}

	private Season testSeason() {
		return seasonRepository.findByYearAndNumber(2026, 99).getFirst();
	}

	private UUID regularPhaseId(UUID seasonId) {
		return seasonPhaseService.findByType(seasonId, PhaseType.REGULAR).orElseThrow().getId();
	}
}
