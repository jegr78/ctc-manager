package org.ctc.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.ctc.TestHelper;
import org.ctc.domain.model.Driver;
import org.ctc.domain.model.Match;
import org.ctc.domain.model.Matchday;
import org.ctc.domain.model.Race;
import org.ctc.domain.model.RaceResult;
import org.ctc.domain.model.Season;
import org.ctc.domain.model.Team;
import org.ctc.domain.repository.MatchRepository;
import org.ctc.domain.repository.RaceRepository;
import org.ctc.domain.repository.RaceResultRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("dev")
@Tag("integration")
@Transactional
class StandingsServicePhaseScopedStaleDetectionIT {

	@Autowired
	StandingsService standingsService;

	@Autowired
	TestHelper helper;

	@Autowired
	MatchRepository matchRepository;

	@Autowired
	RaceRepository raceRepository;

	@Autowired
	RaceResultRepository raceResultRepository;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@PersistenceContext
	EntityManager entityManager;

	private Race seedRaceWithResult(String suffix, LocalDateTime resultUpdatedAt) {
		Season season = helper.createSeason("Stale " + suffix);
		Matchday md = helper.createMatchdayInRegularPhase(season, "MD-Stale-" + suffix, 0);
		Team home = helper.createTeam("Stale Home " + suffix, "sh" + suffix);
		Team away = helper.createTeam("Stale Away " + suffix, "sa" + suffix);
		Driver driver = helper.createDriver("stale-psn-" + suffix, "Stale Drv " + suffix);
		Match match = helper.createMatch(md, home, away);
		Race race = helper.createRace(md, match);
		raceRepository.save(race);
		match.getRaces().add(race);
		matchRepository.save(match);

		RaceResult result = new RaceResult();
		result.setRace(race);
		result.setDriver(driver);
		result.setPosition(1);
		result.setQualiPosition(1);
		RaceResult savedResult = raceResultRepository.save(result);
		entityManager.flush();
		jdbcTemplate.update("UPDATE race_results SET updated_at = ? WHERE id = ?",
				resultUpdatedAt, savedResult.getId());
		entityManager.clear();
		return race;
	}

	@Test
	void givenResultNewerThanSince_whenHasNewerResultsSincePhaseScoped_thenTrue() {
		LocalDateTime since = LocalDateTime.of(2026, 5, 1, 12, 0);
		Race race = seedRaceWithResult("R1", since.plusDays(1));
		var phaseId = race.getMatchday().getPhase().getId();

		boolean stale = standingsService.hasNewerResultsSincePhaseScoped(phaseId, since);

		assertThat(stale).isTrue();
	}

	@Test
	void givenResultEqualToSince_whenHasNewerResultsSincePhaseScoped_thenFalse() {
		LocalDateTime since = LocalDateTime.of(2026, 5, 10, 12, 0);
		Race race = seedRaceWithResult("R2", since);
		var phaseId = race.getMatchday().getPhase().getId();

		boolean stale = standingsService.hasNewerResultsSincePhaseScoped(phaseId, since);

		assertThat(stale).isFalse();
	}

	@Test
	void givenResultOlderThanSince_whenHasNewerResultsSincePhaseScoped_thenFalse() {
		LocalDateTime since = LocalDateTime.of(2026, 5, 20, 12, 0);
		Race race = seedRaceWithResult("R3", since.minusHours(1));
		var phaseId = race.getMatchday().getPhase().getId();

		boolean stale = standingsService.hasNewerResultsSincePhaseScoped(phaseId, since);

		assertThat(stale).isFalse();
	}

	@Test
	void givenNoResults_whenHasNewerResultsSincePhaseScoped_thenFalse() {
		Season season = helper.createSeason("Stale R4");
		Matchday md = helper.createMatchdayInRegularPhase(season, "MD-Stale-R4", 0);
		var phaseId = md.getPhase().getId();

		boolean stale = standingsService.hasNewerResultsSincePhaseScoped(
				phaseId, LocalDateTime.of(2026, 5, 1, 12, 0));

		assertThat(stale).isFalse();
	}

	@Test
	void givenSinceIsNull_whenHasNewerResultsSincePhaseScoped_thenFalse() {
		Race race = seedRaceWithResult("R5", LocalDateTime.now());
		var phaseId = race.getMatchday().getPhase().getId();

		boolean stale = standingsService.hasNewerResultsSincePhaseScoped(phaseId, null);

		assertThat(stale).isFalse();
	}
}
