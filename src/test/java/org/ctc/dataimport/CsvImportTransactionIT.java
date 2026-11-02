package org.ctc.dataimport;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.List;
import java.util.UUID;
import org.ctc.TestHelper;
import org.ctc.TestHelper.SeasonFixture;
import org.ctc.domain.model.*;
import org.ctc.domain.repository.*;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionTemplate;
import org.ctc.dataimport.exception.ImportRejectedException;
import org.ctc.domain.service.RaceService;
import org.ctc.domain.service.SeasonCarPoolService;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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

	@MockitoSpyBean
	RaceRepository raceRepository;

	@MockitoSpyBean
	DriverRepository driverRepository;

	@Autowired
	MatchdayRepository matchdayRepository;

	@Autowired
	TeamRepository teamRepository;

	@Autowired
	JdbcTemplate jdbcTemplate;

    @Autowired SeasonRepository seasons;
    @Autowired CarRepository cars;
    @Autowired TransactionTemplate tx;
    @Autowired SeasonCarPoolService pools;
    @Autowired RaceService raceService;
    @Autowired CsvImportService imports;
    List<UUID> categoryCars = new ArrayList<>();
    List<Team> additionalTeams = new ArrayList<>();
    UUID categoryEntry;
    UUID alternativeEntry;

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
        cars.deleteAllById(categoryCars);
        teamRepository.deleteAll(additionalTeams);
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
	void givenOverwriteWithValidRows_whenImportExecuted_thenTheOldRaceIsReplacedByTheImportedOne() throws Exception {
		// given
		String csv = """
				Team,PSN ID,Position,Quali,FL
				%s,%s,2,1,false
				%s,%s,1,2,true
				""".formatted(home(), existingDriver.getPsnId(), away(), fuzzyDriver.getPsnId());

		// when
		mockMvc.perform(multipart("/admin/import/execute")
						.file(csvFile(csv))
						.param("seasonId", fixture.season().getId().toString())
						.param("matchdayId", fixture.matchday().getId().toString())
						.param("overwrite", "true"))
				.andExpect(status().is3xxRedirection())
				.andExpect(flash().attribute("successMessage", containsString("Import successful: 1 races")));

		// then
		List<Race> races = raceRepository.findByMatchId(fixture.match().getId());
		assertThat(races).as("overwrite replaces the old race").singleElement()
				.extracting(Race::getId).isNotEqualTo(fixture.race().getId());
		assertThat(resultCount(races.getFirst().getId())).isEqualTo(2);
	}

    @Test
    void givenConfiguredCategoryRace_whenOverwritingSingleReversedScorecard_thenKeepIdSettingsAndOwnership() throws Exception {
        // given
        prepareCategoryPool(false);
        configureCategory(fixture.race().getId(), categoryEntry, categoryCars.get(0));
        String csv = scorecard(away(), fuzzyDriver.getPsnId(), home(), existingDriver.getPsnId());
        // when
        mockMvc.perform(multipart("/admin/import/execute").file(csvFile(csv))
                        .param("seasonId", fixture.season().getId().toString())
                        .param("matchdayId", fixture.matchday().getId().toString()).param("overwrite", "true"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("successMessage", containsString("Import successful: 1 races")));
        // then
        assertConfigured(fixture.race().getId(), fixture.homeTeam().getId(), categoryEntry, categoryCars.get(0));
        assertThat(resultCount(fixture.race().getId())).isEqualTo(2);
    }

    @Test
    void givenConfiguredHomeAndReturnLeg_whenImportingReturnFirst_thenPreserveEachStableRaceAndSelection() throws Exception {
        // given
        prepareCategoryPool(false);
        configureCategory(fixture.race().getId(), categoryEntry, categoryCars.get(0));
        UUID reverse = createAdditionalLeg(true);
        configureCategory(reverse, categoryEntry, categoryCars.get(1));
        var returning = preview(scorecard(away(), fuzzyDriver.getPsnId(), home(), existingDriver.getPsnId()));
        var outbound = preview(scorecard(home(), existingDriver.getPsnId(), away(), fuzzyDriver.getPsnId()));
        // when
        var result = imports.executeMultiRaceImport(List.of(returning, outbound), Map.of(), Set.of(), true);
        // then
        assertThat(result.getImportedRaces()).hasSize(2);
        assertThat(raceRepository.findByMatchId(fixture.match().getId())).extracting(Race::getId)
                .containsExactlyInAnyOrder(fixture.race().getId(), reverse);
        assertConfigured(fixture.race().getId(), fixture.homeTeam().getId(), categoryEntry, categoryCars.get(0));
        assertConfigured(reverse, fixture.awayTeam().getId(), categoryEntry, categoryCars.get(1));
        assertThat(resultCount(fixture.race().getId())).isEqualTo(2);
        assertThat(resultCount(reverse)).isEqualTo(2);
    }

    @Test
    void givenEarlierBlankPairingAndAmbiguousConfiguredLegs_whenImportingAll_thenPreflightBeforeAnyDeletionOrDriverCreation() throws Exception {
        // given
        prepareCategoryPool(true);
        configureCategory(fixture.race().getId(), categoryEntry, categoryCars.get(0));
        UUID ambiguous = createAdditionalLeg(false);
        configureCategory(ambiguous, alternativeEntry, categoryCars.get(2));
        UUID blank = tx.execute(status -> {
            var season = seasons.findById(fixture.season().getId()).orElseThrow();
            var c = testHelper.createTeam("Test CsvTx C " + id, "TC" + id);
            var d = testHelper.createTeam("Test CsvTx D " + id, "TD" + id);
            additionalTeams.addAll(List.of(c, d));
            season.addTeam(c);
            season.addTeam(d);
            var md = matchdayRepository.findById(fixture.matchday().getId()).orElseThrow();
            var match = testHelper.createMatch(md, c, d);
            return testHelper.createRace(md, match).getId();
        });
        String newPsn = "zz" + id + "PreflightOnly";
        var earlier = preview(scorecard(additionalTeams.get(0).getShortName(), newPsn,
                additionalTeams.get(1).getShortName(), fuzzyDriver.getPsnId()));
        var repeated = preview(scorecard(home(), existingDriver.getPsnId(), away(), fuzzyDriver.getPsnId()));
        // when / then
        assertThatThrownBy(() -> imports.executeMultiRaceImport(List.of(earlier, repeated, repeated), Map.of(), Set.of(), true))
                .isInstanceOf(ImportRejectedException.class).hasMessageContaining("ambiguous");
        verify(raceRepository, never()).delete(argThat(race -> blank.equals(race.getId())));
        verify(driverRepository, never()).save(argThat(driver -> newPsn.equals(driver.getPsnId())));
        assertThat(driverRepository.findByPsnId(newPsn)).isEmpty();
        assertThat(raceRepository.findByMatchId(fixture.match().getId())).extracting(Race::getId)
                .containsExactlyInAnyOrder(fixture.race().getId(), ambiguous);
        assertThat(resultCount(fixture.race().getId())).isEqualTo(1);
        assertThat(resultCount(ambiguous)).isEqualTo(1);
        assertConfigured(fixture.race().getId(), fixture.homeTeam().getId(), categoryEntry, categoryCars.get(0));
        assertConfigured(ambiguous, fixture.homeTeam().getId(), alternativeEntry, categoryCars.get(2));
    }

    private void prepareCategoryPool(boolean withAlternative) {
        tx.executeWithoutResult(status -> {
            var season = seasons.findById(fixture.season().getId()).orElseThrow();
            for (String name : withAlternative ? List.of("MR", "RR", "FR") : List.of("MR", "RR")) {
                var car = new Car("Test CSV category " + id, name);
                car.setCategory(CarCategory.GR_3);
                car.setDrivetrain(Drivetrain.valueOf(name));
                car.setModelYear(9897);
                car.setModelYearOverride(true);
                car.setModelYearSource("Test fixture");
                car.setModelYearEvidence("Test accepted local year");
                categoryCars.add(cars.saveAndFlush(car).getId());
            }
            pools.changeMode(season.getId(), CarPoolMode.CATEGORY_POOL);
            categoryEntry = pools.saveEntry(season.getId(), null, "Test CSV MR/RR", List.of(
                    new SeasonCarPoolService.ClauseInput(CarCategory.GR_3, Drivetrain.MR, 9897),
                    new SeasonCarPoolService.ClauseInput(CarCategory.GR_3, Drivetrain.RR, 9897)));
            if (withAlternative) alternativeEntry = pools.saveEntry(season.getId(), null, "Test CSV FR", List.of(
                    new SeasonCarPoolService.ClauseInput(CarCategory.GR_3, Drivetrain.FR, 9897)));
            pools.freeze(season.getId());
        });
    }

    private void configureCategory(UUID raceId, UUID entry, UUID car) {
        var context = tx.execute(status -> {
            var race = raceRepository.findById(raceId).orElseThrow();
            return List.of(race.getHomeTeam().getId(), race.getAwayTeam().getId());
        });
        assertThat(raceService.saveRace(raceId, fixture.matchday().getId(), context.get(0), context.get(1), null, car, null,
                17, null, null, null, null, null, TimeProgressionMode.MULTIPLIER, 0, null, null, null, null, entry).success()).isTrue();
    }

    private UUID createAdditionalLeg(boolean reverse) {
        return tx.execute(status -> {
            var original = raceRepository.findById(fixture.race().getId()).orElseThrow();
            var race = new Race();
            race.setMatch(original.getMatch());
            race.setMatchday(original.getMatchday());
            if (reverse) {
                race.setHomeTeamOverride(original.getAwayTeam());
                race.setAwayTeamOverride(original.getHomeTeam());
            }
            race.getResults().add(new RaceResult(race, existingDriver, 1, 1, false));
            return raceRepository.saveAndFlush(race).getId();
        });
    }

    private CsvImportService.ImportPreview preview(String csv) throws Exception {
        return tx.execute(status -> {
            try {
                return imports.parseAndPreview(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)),
                        new CsvImportService.ImportMetadata(fixture.season().getId(), null, null, null, null, fixture.matchday().getId()));
            } catch (java.io.IOException ex) {
                throw new java.io.UncheckedIOException(ex);
            }
        });
    }

    private static String scorecard(String firstTeam, String firstDriver, String secondTeam, String secondDriver) {
        return "Team,PSN ID,Position,Quali,FL\n%s,%s,1,1,true\n%s,%s,2,2,false\n"
                .formatted(firstTeam, firstDriver, secondTeam, secondDriver);
    }

    private void assertConfigured(UUID raceId, UUID home, UUID entry, UUID car) {
        tx.executeWithoutResult(status -> {
            var saved = raceRepository.findById(raceId).orElseThrow();
            assertThat(saved.getHomeTeam().getId()).isEqualTo(home);
            assertThat(saved.getCarPoolEntry().getId()).isEqualTo(entry);
            assertThat(saved.getCar().getId()).isEqualTo(car);
            assertThat(saved.getSettings().getNumberOfLaps()).isEqualTo(17);
        });
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

	@Test
	void givenMatchdayOfAnotherSeason_whenImportExecuted_thenRejectedAndNothingIsCreated() throws Exception {
		// given
		var other = testHelper.createSeason("Test_CsvTx_Other_" + id);
		var foreignMatchday = testHelper.createMatchdayInRegularPhase(other, "Test_CsvTx_" + id + " Foreign", 1);
		String csv = """
				Team,PSN ID,Position,Quali,FL
				%s,%s,1,1,true
				%s,%s,2,2,false
				""".formatted(home(), existingDriver.getPsnId(), away(), fuzzyDriver.getPsnId());

		// when
		mockMvc.perform(multipart("/admin/import/execute")
						.file(csvFile(csv))
						.param("seasonId", fixture.season().getId().toString())
						.param("matchdayId", foreignMatchday.getId().toString()))
				.andExpect(status().is3xxRedirection())
				.andExpect(flash().attribute("errorMessage", allOf(
						containsString("Import rejected, nothing was imported"),
						containsString("The matchday does not belong to the selected season"))));

		// then
		assertThat(raceRepository.findByMatchdayId(foreignMatchday.getId()))
				.as("no race may land in the other season's matchday").isEmpty();
		testHelper.deleteSeasonCascade(other);
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
