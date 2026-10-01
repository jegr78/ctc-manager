package org.ctc.domain.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.ctc.TestHelper;
import org.ctc.TestHelper.SeasonFixture;
import org.ctc.domain.model.Car;
import org.ctc.domain.model.Race;
import org.ctc.domain.model.Team;
import org.ctc.domain.repository.CarRepository;
import org.ctc.domain.repository.MatchRepository;
import org.ctc.domain.repository.RaceRepository;
import org.ctc.domain.repository.SeasonRepository;
import org.ctc.domain.repository.TeamRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs {@link RaceService#saveRace} against the real database without a test transaction, so a
 * rejected save that still flushes dirty entities becomes visible.
 */
@SpringBootTest
@ActiveProfiles("dev")
@Tag("integration")
class RaceServiceSaveRejectionIT {

	private static final LocalDateTime ORIGINAL_TIME = LocalDateTime.of(2026, 5, 1, 20, 0);

	@Autowired
	RaceService raceService;

	@Autowired
	TestHelper testHelper;

	@Autowired
	RaceRepository raceRepository;

	@Autowired
	MatchRepository matchRepository;

	@Autowired
	SeasonRepository seasonRepository;

	@Autowired
	TeamRepository teamRepository;

	@Autowired
	CarRepository carRepository;

	@Autowired
	TransactionTemplate transactionTemplate;

	private final String id = UUID.randomUUID().toString().substring(0, 8);
	private SeasonFixture fixture;
	private Team otherTeam;
	private Car poolCar;
	private Car foreignCar;

	@BeforeEach
	void createSeasonWithScheduledRace() {
		fixture = testHelper.createFullSeasonFixture("Test_RaceRej_" + id);
		otherTeam = testHelper.createTeam("Test RaceRej Other " + id, "Test_RaceRej_" + id + "_OTH");
		poolCar = carRepository.save(new Car("Test_RaceRej", "Pool " + id));
		foreignCar = carRepository.save(new Car("Test_RaceRej", "Foreign " + id));
		transactionTemplate.executeWithoutResult(status -> {
			var season = seasonRepository.findById(fixture.season().getId()).orElseThrow();
			season.addTeam(otherTeam);
			season.getCars().add(poolCar);
		});

		Race race = fixture.race();
		race.setDateTime(ORIGINAL_TIME);
		var settings = testHelper.completeRaceSettings(race);
		race.setSettings(settings);
		raceRepository.save(race);
	}

	@AfterEach
	void removeFixture() {
		transactionTemplate.executeWithoutResult(status ->
				seasonRepository.findById(fixture.season().getId()).orElseThrow().getCars().clear());
		testHelper.deleteSeasonCascade(fixture.season());
		teamRepository.deleteAll(List.of(fixture.homeTeam(), fixture.awayTeam(), otherTeam));
		carRepository.deleteAll(List.of(poolCar, foreignCar));
	}

	@Test
	void givenCarOutsideThePool_whenEditingARace_thenRaceMatchAndSettingsStayUnchanged() {
		// when
		var result = saveEdit(fixture.race().getId(), otherTeam, foreignCar);

		// then
		assertThat(result.success()).isFalse();
		assertThat(result.message()).isEqualTo("Car is not in this season's pool");
		assertRaceUnchanged();
	}

	@Test
	void givenCarTheHomeTeamAlreadyUsed_whenEditingARace_thenRaceMatchAndSettingsStayUnchanged() {
		// given
		var secondMatch = testHelper.createMatch(fixture.matchday(), fixture.homeTeam(), otherTeam);
		var secondRace = testHelper.createRace(fixture.matchday(), secondMatch);
		secondRace.setCar(poolCar);
		raceRepository.save(secondRace);

		// when
		var result = saveEdit(fixture.race().getId(), fixture.homeTeam(), poolCar);

		// then
		assertThat(result.success()).isFalse();
		assertThat(result.message()).contains("has already used");
		assertRaceUnchanged();
	}

	@Test
	void givenCarOutsideThePool_whenCreatingARace_thenNoMatchIsLeftBehind() {
		// given
		long matchesBefore = matchRepository.count();

		// when
		var result = saveEdit(null, otherTeam, foreignCar);

		// then
		assertThat(result.success()).isFalse();
		assertThat(matchRepository.count()).as("a rejected creation must not persist its Match").isEqualTo(matchesBefore);
	}

	@Test
	void givenMatchOfTheTwoTeams_whenCreatingARaceInReverseOrientation_thenRejectedWithoutANewMatch() {
		// given
		long matchesBefore = matchRepository.count();

		// when
		var result = raceService.saveRace(null, fixture.matchday().getId(), fixture.awayTeam().getId(),
				fixture.homeTeam().getId(), null, null, ORIGINAL_TIME, 5, 1, 1, 1, "100%", 0, 1, "clear", "noon", "any", "none");

		// then
		assertThat(result.success()).as("save of a reversed pairing").isFalse();
		assertThat(result.message()).isEqualTo("Match already exists: %s vs %s".formatted(fixture.homeTeam().getShortName(),
				fixture.awayTeam().getShortName()));
		assertThat(matchRepository.count()).as("matches after the rejected save").isEqualTo(matchesBefore);
	}

	@Test
	void givenAnotherMatchOfTheNewPairing_whenEditingARace_thenRejectedAndUnchanged() {
		// given
		testHelper.createMatch(fixture.matchday(), fixture.awayTeam(), otherTeam);

		// when
		var result = raceService.saveRace(fixture.race().getId(), fixture.matchday().getId(), otherTeam.getId(),
				fixture.awayTeam().getId(), null, null, ORIGINAL_TIME.plusDays(3),
				99, 9, 9, 9, "50%", 3, 9, "rain", "night", "RS", "RH");

		// then
		assertThat(result.success()).as("edit onto an existing pairing").isFalse();
		assertThat(result.message()).isEqualTo("Match already exists: %s vs %s".formatted(fixture.awayTeam().getShortName(),
				otherTeam.getShortName()));
		assertRaceUnchanged();
	}

	private RaceService.SaveResult saveEdit(UUID raceId, Team home, Car car) {
		return raceService.saveRace(raceId, fixture.matchday().getId(), home.getId(), fixture.awayTeam().getId(),
				null, car.getId(), ORIGINAL_TIME.plusDays(3),
				99, 9, 9, 9, "50%", 3, 9, "rain", "night", "RS", "RH");
	}

	private void assertRaceUnchanged() {
		transactionTemplate.executeWithoutResult(status -> {
			Race race = raceRepository.findById(fixture.race().getId()).orElseThrow();
			assertThat(race.getCar()).as("car").isNull();
			assertThat(race.getDateTime()).as("date").isEqualTo(ORIGINAL_TIME);
			assertThat(race.getMatch().getHomeTeam().getId()).as("pairing").isEqualTo(fixture.homeTeam().getId());
			assertThat(race.getSettings().getNumberOfLaps()).as("settings").isEqualTo(5);
			assertThat(race.getSettings().getWeather()).as("settings").isEqualTo("clear");
		});
	}
}
