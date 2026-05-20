package org.ctc.domain.model;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.UUID;
import org.ctc.domain.repository.*;
import org.ctc.testsupport.CtcDevSpringBootContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for the new Season -> Phase -> Group -> Team hierarchy.
 *
 * Verifies that the three new entities (`SeasonPhase`, `SeasonPhaseGroup`, `PhaseTeam`)
 * persist correctly against the V3 schema (H2 in-memory on the dev profile),
 * that BaseEntity audit columns are populated, and that the bidirectional
 * fields added in Plan 56-04 (`Season.phases`, `Matchday.phase`, `Playoff.phase`)
 * round-trip through JPA.
 *
 * Uses SpringBootTest with the dev profile and class-level transactional
 * rollback per project precedent — see {@code BaseEntityAuditTest}; the
 * codebase has no DataJpaTest usages, so the persistence-integration
 * convention is the full Spring context.
 *
 * Test data uses the "Phase56-Test" prefix to comply with the CLAUDE.md rule
 * "Isolate Test Data Completely". `@Transactional` rolls each test back so no
 * rows leak between tests or into the dev seed data.
 */
@CtcDevSpringBootContext
@Transactional
class SeasonPhaseEntityIntegrationTest {

	@Autowired
	private SeasonRepository seasonRepository;

	@Autowired
	private TeamRepository teamRepository;

	@Autowired
	private RaceScoringRepository raceScoringRepository;

	@Autowired
	private MatchScoringRepository matchScoringRepository;

	@Autowired
	private SeasonPhaseRepository seasonPhaseRepository;

	@Autowired
	private SeasonPhaseGroupRepository seasonPhaseGroupRepository;

	@Autowired
	private PhaseTeamRepository phaseTeamRepository;

	@Autowired
	private MatchdayRepository matchdayRepository;

	@Autowired
	private PlayoffRepository playoffRepository;

	@PersistenceContext
	private EntityManager entityManager;

	@Test
	void givenNewSeasonPhase_whenSaved_thenAuditFieldsArePopulated() {
		// given
		var season = newSeason("Phase56-Test-S1", 9999, 1);
		var savedSeason = seasonRepository.save(season);
		var phase = new SeasonPhase(savedSeason, PhaseType.REGULAR, PhaseLayout.LEAGUE, 0);
		// scoring is now phase-owned; pull from repositories.
		phase.setRaceScoring(raceScoringRepository.findAll().get(0));
		phase.setMatchScoring(matchScoringRepository.findAll().get(0));
		assertThat(phase.getId()).isNull();
		assertThat(phase.getCreatedAt()).isNull();
		assertThat(phase.getUpdatedAt()).isNull();

		// when
		var saved = seasonPhaseRepository.save(phase);

		// then
		assertThat(saved.getId()).isNotNull();
		assertThat(saved.getCreatedAt()).isNotNull();
		assertThat(saved.getUpdatedAt()).isNotNull();
		assertThat(saved.getPhaseType()).isEqualTo(PhaseType.REGULAR);
		assertThat(saved.getLayout()).isEqualTo(PhaseLayout.LEAGUE);
	}

	@Test
	void givenSeasonPhaseWithGroups_whenReloaded_thenGroupsCollectionIsOrderedBySortIndex() {
		// given
		var season = seasonRepository.save(newSeason("Phase56-Test-S2", 9999, 2));
		var phase = new SeasonPhase(season, PhaseType.REGULAR, PhaseLayout.GROUPS, 0);
		phase.setRaceScoring(raceScoringRepository.findAll().get(0));
		phase.setMatchScoring(matchScoringRepository.findAll().get(0));
		var savedPhase = seasonPhaseRepository.save(phase);
		seasonPhaseGroupRepository.save(new SeasonPhaseGroup(savedPhase, "Phase56-Test-B", 2));
		seasonPhaseGroupRepository.save(new SeasonPhaseGroup(savedPhase, "Phase56-Test-A", 0));
		seasonPhaseGroupRepository.save(new SeasonPhaseGroup(savedPhase, "Phase56-Test-C", 1));
		entityManager.flush();
		entityManager.clear();

		// when
		var reloaded = seasonPhaseRepository.findById(savedPhase.getId()).orElseThrow();
		List<SeasonPhaseGroup> groups = reloaded.getGroups();

		// then
		assertThat(groups).hasSize(3);
		assertThat(groups).extracting(SeasonPhaseGroup::getSortIndex).containsExactly(0, 1, 2);
		assertThat(groups).extracting(SeasonPhaseGroup::getName)
				.containsExactly("Phase56-Test-A", "Phase56-Test-C", "Phase56-Test-B");
	}

	@Test
	void givenPhaseTeamWithoutGroup_whenSaved_thenGroupIsNull() {
		// given
		var season = seasonRepository.save(newSeason("Phase56-Test-S3", 9999, 3));
		var phase = newSavedPhase(season, PhaseType.REGULAR, PhaseLayout.LEAGUE, 0);
		var team = teamRepository.save(new Team("Phase56-Test-Team-A", "P56A"));
		var phaseTeam = new PhaseTeam(phase, team);

		// when
		var savedId = phaseTeamRepository.save(phaseTeam).getId();
		var reloaded = phaseTeamRepository.findById(savedId).orElseThrow();

		// then
		assertThat(reloaded.getGroup()).isNull();
		assertThat(reloaded.getPhase().getId()).isEqualTo(phase.getId());
		assertThat(reloaded.getTeam().getId()).isEqualTo(team.getId());
		assertThat(reloaded.getCreatedAt()).isNotNull();
	}

	@Test
	void givenSeasonWithPhases_whenReloaded_thenSeasonPhasesCollectionContainsTheSavedPhase() {
		// given
		var season = seasonRepository.save(newSeason("Phase56-Test-S4", 9999, 4));
		newSavedPhase(season, PhaseType.REGULAR, PhaseLayout.LEAGUE, 0);
		UUID seasonId = season.getId();
		entityManager.flush();
		entityManager.clear();

		// when
		var reloaded = seasonRepository.findById(seasonId).orElseThrow();

		// then
		assertThat(reloaded.getPhases()).hasSize(1);
		assertThat(reloaded.getPhases().get(0).getPhaseType()).isEqualTo(PhaseType.REGULAR);
		assertThat(reloaded.getPhases().get(0).getLayout()).isEqualTo(PhaseLayout.LEAGUE);
	}

	@Test
	void givenMatchdayWithPhase_whenSaved_thenPhaseIsReachableOnReload() {
		// given
		var season = seasonRepository.save(newSeason("Phase56-Test-S5", 9999, 5));
		var phase = newSavedPhase(season, PhaseType.REGULAR, PhaseLayout.LEAGUE, 0);
		// Matchday is bound exclusively via phase.
		var matchday = new Matchday(phase, "Phase56-Test-MD1", 0);

		// when
		var savedId = matchdayRepository.saveAndFlush(matchday).getId();
		var reloaded = matchdayRepository.findById(savedId).orElseThrow();

		// then
		assertThat(reloaded.getPhase()).isNotNull();
		assertThat(reloaded.getPhase().getId()).isEqualTo(phase.getId());
		assertThat(reloaded.getSeason().getId()).isEqualTo(season.getId());
	}

	@Test
	void givenPlayoffWithPhase_whenSaved_thenPhaseIsReachableOnReload() {
		// given
		var season = seasonRepository.save(newSeason("Phase56-Test-S6", 9999, 6));
		var phase = newSavedPhase(season, PhaseType.PLAYOFF, PhaseLayout.BRACKET, 1);
		// Playoff is bound exclusively via phase.
		var playoff = new Playoff(phase, "Phase56-Test-Playoff");

		// when
		var savedId = playoffRepository.saveAndFlush(playoff).getId();
		var reloaded = playoffRepository.findById(savedId).orElseThrow();

		// then
		assertThat(reloaded.getPhase()).isNotNull();
		assertThat(reloaded.getPhase().getId()).isEqualTo(phase.getId());
		assertThat(reloaded.getSeason().getId()).isEqualTo(season.getId());
	}


	private Season newSeason(String name, int year, int number) {
		// scoring lives on the SeasonPhase, not the Season.
		return new Season(name, year, number);
	}

	private SeasonPhase newSavedPhase(Season season, PhaseType phaseType, PhaseLayout layout, int sortIndex) {
		var phase = new SeasonPhase(season, phaseType, layout, sortIndex);
		phase.setRaceScoring(raceScoringRepository.findAll().get(0));
		phase.setMatchScoring(matchScoringRepository.findAll().get(0));
		return seasonPhaseRepository.save(phase);
	}
}
