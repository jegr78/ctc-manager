package org.ctc.domain.service;

import java.util.List;
import java.util.UUID;
import org.ctc.TestHelper;
import org.ctc.domain.model.Driver;
import org.ctc.domain.model.Playoff;
import org.ctc.domain.model.RaceResult;
import org.ctc.domain.model.Season;
import org.ctc.domain.repository.PlayoffMatchupRepository;
import org.ctc.domain.repository.RaceRepository;
import org.ctc.domain.repository.SeasonRepository;
import org.ctc.testsupport.CtcDevSpringBootContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@CtcDevSpringBootContext
@Tag("integration")
@Transactional
class DriverRankingPlayoffIT {

	@Autowired private DriverRankingService driverRankingService;
	@Autowired private PlayoffService playoffService;
	@Autowired private ScoringService scoringService;
	@Autowired private TestHelper testHelper;
	@Autowired private SeasonRepository seasonRepository;
	@Autowired private RaceRepository raceRepository;
	@Autowired private PlayoffMatchupRepository playoffMatchupRepository;

	private final String id = UUID.randomUUID().toString().substring(0, 8);
	private Season season;
	private Playoff playoff;
	private Driver driver;

	@BeforeEach
	void createPlayoffRaceWithOneTwentyPointResult() {
		season = testHelper.createSeason("Test_PlayoffRank_" + id);
		var home = testHelper.createTeam("Test PlayoffRank Home " + id, "Test_PRH_" + id);
		var away = testHelper.createTeam("Test PlayoffRank Away " + id, "Test_PRA_" + id);
		season.addTeam(home);
		season.addTeam(away);
		seasonRepository.save(season);
		playoff = playoffService.createPlayoff(season.getId(), "Test PlayoffRank " + id, 2);
		var matchup = playoffMatchupRepository.findAll().stream()
				.filter(m -> m.getRound().getPlayoff().getId().equals(playoff.getId()))
				.findFirst().orElseThrow();
		matchup.setTeam1(home);
		matchup.setTeam2(away);
		var race = playoffService.addRaceToMatchup(matchup.getId(), null, null, null);
		driver = testHelper.createDriver("Test_PlayoffRank_" + id, "Test PlayoffRank Driver");
		testHelper.createSeasonDriver(season, driver, home);
		var result = new RaceResult(race, driver, 1, 5, false);
		scoringService.calculatePoints(result, playoff.getPhase().getRaceScoring());
		race.getResults().add(result);
		raceRepository.saveAndFlush(race);
		assertThat(result.getPointsTotal()).as("fixture: one win without bonus points").isEqualTo(20);
	}

	@Test
	void givenPlayoffRaceFoundByBothPhaseFinders_whenPhaseRankingCalculated_thenTheResultCountsOnce() {
		// when
		var ranking = rankingOf(driverRankingService.calculateRankingForPhase(playoff.getPhase().getId()));

		// then
		assertThat(ranking.getTotalPoints()).as("points").isEqualTo(20);
		assertThat(ranking.getRacesCount()).as("starts").isEqualTo(1);
	}

	@Test
	void givenPlayoffRaceFoundByBothPhaseFinders_whenSeasonRankingAggregated_thenTheResultCountsOnce() {
		// given
		var regularPhaseId = season.getPhases().getFirst().getId();

		// when
		var ranking = rankingOf(driverRankingService.aggregateAcrossPhases(
				List.of(regularPhaseId, playoff.getPhase().getId()), season.getId()));

		// then
		assertThat(ranking.getTotalPoints()).as("points").isEqualTo(20);
		assertThat(ranking.getRacesCount()).as("starts").isEqualTo(1);
	}

	private DriverRankingService.DriverRanking rankingOf(List<DriverRankingService.DriverRanking> rankings) {
		return rankings.stream()
				.filter(r -> r.getDriver().getId().equals(driver.getId()))
				.findFirst().orElseThrow();
	}
}
