package org.ctc.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.List;
import java.util.UUID;
import org.ctc.TestHelper;
import org.ctc.domain.model.Driver;
import org.ctc.domain.model.Match;
import org.ctc.domain.model.Matchday;
import org.ctc.domain.model.Race;
import org.ctc.domain.model.Season;
import org.ctc.domain.model.Team;
import org.ctc.domain.model.RaceResult;
import org.ctc.domain.repository.RaceRepository;
import org.ctc.domain.repository.RaceResultRepository;
import org.ctc.domain.service.RaceService.RaceResultData;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("dev")
@Tag("integration")
class RaceServiceSaveResultsReEditIT {

	@Autowired
	RaceService raceService;

	@Autowired
	RaceRepository raceRepository;

	@Autowired
	RaceResultRepository raceResultRepository;

	@Autowired
	TestHelper helper;

	@Test
	void givenExistingResultForDriver_whenSaveResultsAgainWithSameDriver_thenNoUniqueConstraintViolation() {
		Season season = helper.createSeason("SaveResults ReEdit");
		Matchday md = helper.createMatchdayInRegularPhase(season, "MD-SR-1", 0);
		Team home = helper.createTeam("SR Home", "sr-h");
		Team away = helper.createTeam("SR Away", "sr-a");
		season.addTeam(home);
		season.addTeam(away);
		Match match = helper.createMatch(md, home, away);
		Race race = helper.createRace(md, match);
		Driver driver = helper.createDriver("PSN-SR-1", "SR Driver 1");
		helper.createSeasonDriver(season, driver, home);
		UUID raceId = race.getId();
		UUID driverId = driver.getId();

		raceService.saveResults(raceId, List.of(new RaceResultData(driverId, "PSN-SR-1", "sr-h", 3, 5, false)));

		assertThatCode(() -> raceService.saveResults(raceId,
				List.of(new RaceResultData(driverId, "PSN-SR-1", "sr-h", 1, 2, true))))
				.as("Re-editing a result for the same (race_id, driver_id) must not raise UK_RACE_DRIVER_INDEX_2")
				.doesNotThrowAnyException();

		List<RaceResult> reloaded = raceResultRepository.findByRaceId(raceId);
		assertThat(reloaded).hasSize(1);
		assertThat(reloaded.get(0).getPosition()).isEqualTo(1);
		assertThat(reloaded.get(0).getQualiPosition()).isEqualTo(2);
		assertThat(reloaded.get(0).isFastestLap()).isTrue();
	}
}
