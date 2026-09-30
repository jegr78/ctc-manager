package org.ctc.dataimport;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.ctc.TestHelper;
import org.ctc.TestHelper.SeasonFixture;
import org.ctc.domain.model.Driver;
import org.ctc.domain.model.Race;
import org.ctc.domain.model.RaceResult;
import org.ctc.domain.repository.DriverRepository;
import org.ctc.domain.repository.MatchdayRepository;
import org.ctc.domain.repository.RaceRepository;
import org.ctc.domain.repository.TeamRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Drives the real import controller without a test transaction, so every repository call commits
 * or rolls back exactly as in production.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Tag("integration")
class CsvImportTransactionIT {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	TestHelper testHelper;

	@Autowired
	RaceRepository raceRepository;

	@Autowired
	DriverRepository driverRepository;

	@Autowired
	MatchdayRepository matchdayRepository;

	@Autowired
	TeamRepository teamRepository;

	@Autowired
	JdbcTemplate jdbcTemplate;

	private final String id = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
	private SeasonFixture fixture;
	private Driver existingDriver;
	private Driver fuzzyDriver;

	@BeforeEach
	void createFixtureWithOneImportedRace() {
		fixture = testHelper.createFullSeasonFixture("Test_CsvTx_" + id);
		existingDriver = testHelper.createDriver("Test_CsvTx_" + id + "_Alpha", "Test CsvTx Alpha");
		fuzzyDriver = testHelper.createDriver("Qx" + id + "Fuzzy", "Test CsvTx Fuzzy");
		Race race = fixture.race();
		race.getResults().add(new RaceResult(race, existingDriver, 1, 1, false));
		raceRepository.save(race);
	}

	@AfterEach
	void removeFixture() {
		testHelper.deleteSeasonCascade(fixture.season());
		teamRepository.deleteAll(List.of(fixture.homeTeam(), fixture.awayTeam()));
		driverRepository.findAll().stream()
				.filter(d -> d.getPsnId().contains(id))
				.forEach(driverRepository::delete);
	}

	@Test
	void givenOverwriteAndAnUnassignableDriver_whenImportExecuted_thenDeletedRacesAreRestoredAndNothingIsCreated()
			throws Exception {
		// given
		String newDriverPsn = "zz" + id + "NewcomerWithoutAnyMatch";
		String csv = """
				Team,PSN ID,Position,Quali,FL
				%s,%s,1,1,true
				%s,%s,2,2,false
				%s,Qx%sFuzzi,3,3,false
				""".formatted(home(), existingDriver.getPsnId(), away(), newDriverPsn, home(), id);

		// when
		mockMvc.perform(multipart("/admin/import/execute")
						.file(csvFile(csv))
						.param("seasonId", fixture.season().getId().toString())
						.param("matchdayId", fixture.matchday().getId().toString())
						.param("overwrite", "true"))
				.andExpect(status().is3xxRedirection())
				.andExpect(flash().attributeCount(1))
				.andExpect(flash().attribute("errorMessage", allOf(
						containsString("Import rejected, nothing was imported"),
						containsString("Driver could not be assigned: Qx" + id + "Fuzzi"))));

		// then
		assertThat(raceRepository.findByMatchId(fixture.match().getId()))
				.as("the overwritten race must survive the rejected import")
				.extracting(Race::getId).containsExactly(fixture.race().getId());
		assertThat(resultCount(fixture.race().getId())).as("its result must survive too").isEqualTo(1);
		assertThat(driverRepository.findByPsnId(newDriverPsn)).as("no driver may be created").isEmpty();
	}

	@Test
	void givenMalformedRows_whenImportExecuted_thenEveryRowErrorIsReportedAndNothingIsCreated() throws Exception {
		// given
		String newMatchdayLabel = "Test_CsvTx_" + id + " New MD";
		String csv = """
				Team,PSN ID,Position,Quali,FL
				%s,%s,1,1,true
				%s,too-few-columns
				%s,zz%sRowWithBadPosition,first,2,false
				""".formatted(home(), existingDriver.getPsnId(), away(), away(), id);

		// when
		mockMvc.perform(multipart("/admin/import/execute")
						.file(csvFile(csv))
						.param("seasonId", fixture.season().getId().toString())
						.param("matchdayLabel", newMatchdayLabel))
				.andExpect(status().is3xxRedirection())
				.andExpect(flash().attribute("errorMessage", allOf(
						containsString("Import rejected, nothing was imported"),
						containsString("Row 3: Too few columns"),
						containsString("Row 4: Invalid value for Position"))));

		// then
		assertThat(matchdayRepository.findBySeasonIdOrderBySortIndexAsc(fixture.season().getId()))
				.as("no matchday may be created").hasSize(1);
		assertThat(raceRepository.findByMatchId(fixture.match().getId())).hasSize(1);
	}

	private String home() {
		return fixture.homeTeam().getShortName();
	}

	private String away() {
		return fixture.awayTeam().getShortName();
	}

	private long resultCount(UUID raceId) {
		Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM race_results WHERE race_id = ?", Long.class,
				raceId);
		return count == null ? 0 : count;
	}

	private static MockMultipartFile csvFile(String csv) {
		return new MockMultipartFile("file", "scorecard.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));
	}
}
